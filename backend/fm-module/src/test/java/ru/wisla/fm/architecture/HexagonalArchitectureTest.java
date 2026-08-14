package ru.wisla.fm.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the ADR-001 dependency directions for the migrated contexts. Bounded
 * contexts other than {@code ingestion}, {@code processing}, and {@code health}
 * keep the layered Spring/JPA structure, so every layering rule is scoped by
 * {@link #IN_SCOPE} instead of being applied module-wide. Only the
 * service-independence rule is module-wide, and only the transport rules are narrower than
 * {@link #IN_SCOPE} — see {@link #TRANSPORT_IN_SCOPE}.
 */
class HexagonalArchitectureTest {

    private static final String[] IN_SCOPE = {
            "ru.wisla.fm.ingestion..",
            "ru.wisla.fm.processing..",
            "ru.wisla.fm.health.."
    };

    /**
     * The transport rules cannot use {@link #IN_SCOPE}: {@code processing/api/EventController} is a
     * {@code @RestController} that design decision D7 deliberately leaves outside
     * {@code adapter/in}, together with the console services it reads. Widening the transport rules
     * to the processing context therefore requires moving that controller, which this change does
     * not do — so they stay scoped to the contexts where the rule already holds.
     */
    private static final String[] TRANSPORT_IN_SCOPE = {
            "ru.wisla.fm.ingestion..",
            "ru.wisla.fm.health.."
    };

    private static final String[] FRAMEWORK_PACKAGES = {
            "org.springframework..",
            "jakarta.persistence..",
            "org.hibernate..",
            "com.fasterxml.jackson..",
            "org.apache.kafka..",
            "org.springframework.kafka..",
            "jakarta.servlet.."
    };

    private static final String[] FRAMEWORK_AND_OUTER_PACKAGES = {
            "org.springframework..",
            "jakarta.persistence..",
            "org.hibernate..",
            "com.fasterxml.jackson..",
            "org.apache.kafka..",
            "org.springframework.kafka..",
            "jakarta.servlet..",
            "..adapter..",
            "..infrastructure.."
    };

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("ru.wisla.fm");

    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses()
                .that().resideInAnyPackage(IN_SCOPE)
                .and().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
                .because("the domain model must stay plain Java (ADR-001)")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnFrameworksAdaptersOrInfrastructure() {
        noClasses()
                .that().resideInAnyPackage(IN_SCOPE)
                .and().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_AND_OUTER_PACKAGES)
                .because("use cases and ports depend only on the domain and on their own port interfaces")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainDoesNotDependOnApplicationOrAdapters() {
        noClasses()
                .that().resideInAnyPackage(IN_SCOPE)
                .and().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..application..", "..adapter..")
                .because("the domain is the innermost layer")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void persistenceEntitiesResideOnlyInTheOutboundPersistenceAdapter() {
        noClasses()
                .that().resideInAnyPackage(IN_SCOPE)
                .and().resideOutsideOfPackage("..adapter.out.persistence..")
                .should().beAnnotatedWith(Entity.class)
                .orShould().beAnnotatedWith(Table.class)
                .because("JPA mappings belong to the outbound persistence adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void springDataRepositoriesResideOnlyInTheOutboundPersistenceAdapter() {
        noClasses()
                .that().resideInAnyPackage(IN_SCOPE)
                .and().resideOutsideOfPackage("..adapter.out.persistence..")
                .should().beAssignableTo(Repository.class)
                .because("Spring Data repositories belong to the outbound persistence adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void transportAnnotatedClassesResideOnlyInInboundAdapters() {
        noClasses()
                .that().resideInAnyPackage(TRANSPORT_IN_SCOPE)
                .and().resideOutsideOfPackage("..adapter.in..")
                .should().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(KafkaListener.class)
                .because("transport entry points belong to adapter/in")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void transportAnnotatedMethodsResideOnlyInInboundAdapters() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAnyPackage(TRANSPORT_IN_SCOPE)
                .and().areDeclaredInClassesThat().resideOutsideOfPackage("..adapter.in..")
                .should().beAnnotatedWith(Scheduled.class)
                .orShould().beAnnotatedWith(KafkaListener.class)
                .because("scheduled and Kafka-triggered entry points belong to adapter/in")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * {@code Infrastructure} may depend on every layer, and is itself reachable only from
     * {@code Adapter}: {@code infrastructure/config} owns the Spring wiring and the
     * {@code @ConfigurationProperties} records that adapters read. {@code domain} and
     * {@code application} stay cut off from it, which is what ADR-001 requires and what the two
     * rules above assert directly.
     *
     * <p>{@code consideringOnlyDependenciesInLayers()} keeps the out-of-scope contexts out of the
     * check: classes such as {@code processing/api/EventQueryService} or {@code config/DevDataSeeder}
     * reside in no layer, so their reads of this context's JPA types are not judged here.
     */
    @Test
    void ingestionLayersOnlyDependInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("ru.wisla.fm.ingestion.domain..")
                .layer("Application").definedBy("ru.wisla.fm.ingestion.application..")
                .layer("Adapter").definedBy("ru.wisla.fm.ingestion.adapter..")
                .layer("Infrastructure").definedBy("ru.wisla.fm.ingestion.infrastructure..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * The same layering for the processing context. {@code consideringOnlyDependenciesInLayers()}
     * matters more here than for {@code ingestion}: the console half of this context
     * ({@code processing/api}, {@code processing/service}) resides in no layer at all, and design
     * decision D7 keeps it reading {@code adapter/out/persistence} directly.
     */
    @Test
    void processingLayersOnlyDependInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("ru.wisla.fm.processing.domain..")
                .layer("Application").definedBy("ru.wisla.fm.processing.application..")
                .layer("Adapter").definedBy("ru.wisla.fm.processing.adapter..")
                .layer("Infrastructure").definedBy("ru.wisla.fm.processing.infrastructure..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void healthLayersOnlyDependInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("ru.wisla.fm.health.domain..")
                .layer("Application").definedBy("ru.wisla.fm.health.application..")
                .layer("Adapter").definedBy("ru.wisla.fm.health.adapter..")
                .layer("Infrastructure").definedBy("ru.wisla.fm.health.infrastructure..")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter", "Infrastructure")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter", "Infrastructure")
                .whereLayer("Adapter").mayOnlyBeAccessedByLayers("Infrastructure")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Adapter")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * Service independence (design decision D0): {@code backend/adapter} and
     * {@code backend/fm-module} are two independently deployable services that integrate over
     * Kafka and HTTP only. No shared class, no shared module — not even the wire-contract types.
     * This is the one rule that is module-wide rather than scoped to the migrated contexts.
     */
    @Test
    void fmModuleDoesNotDependOnTheAdapterService() {
        noClasses()
                .that().resideInAPackage("ru.wisla.fm..")
                .should().dependOnClassesThat().resideInAnyPackage("com.wisla.fm.adapter..")
                .because("the two services stay compile-time independent (D0)")
                .check(PRODUCTION_CLASSES);
    }
}

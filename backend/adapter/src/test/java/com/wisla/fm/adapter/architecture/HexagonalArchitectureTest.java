package com.wisla.fm.adapter.architecture;

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
 * Enforces the ADR-001 dependency directions for the migrated {@code ingest} context.
 *
 * <p>Package patterns are spelled out in full rather than as relative {@code ..domain..} /
 * {@code ..adapter..} patterns, because this module's root package is
 * {@code com.wisla.fm.adapter}: the pattern {@code ..adapter..} matches every class in the
 * module, including {@code ingest.domain}, and would turn "application must not depend on
 * adapter" into "application must not depend on anything".
 */
class HexagonalArchitectureTest {

    private static final String CONTEXT = "com.wisla.fm.adapter.ingest";
    private static final String DOMAIN = CONTEXT + ".domain..";
    private static final String APPLICATION = CONTEXT + ".application..";
    private static final String ADAPTER = CONTEXT + ".adapter..";
    private static final String INBOUND_ADAPTER = CONTEXT + ".adapter.in..";
    private static final String PERSISTENCE_ADAPTER = CONTEXT + ".adapter.out.persistence..";
    private static final String INFRASTRUCTURE = CONTEXT + ".infrastructure..";

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
            ADAPTER,
            INFRASTRUCTURE
    };

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.wisla.fm.adapter");

    @Test
    void domainDoesNotDependOnFrameworks() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_PACKAGES)
                .because("the domain model must stay plain Java (ADR-001)")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnFrameworksAdaptersOrInfrastructure() {
        noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORK_AND_OUTER_PACKAGES)
                .because("use cases and ports depend only on the domain and on their own port interfaces")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainDoesNotDependOnApplicationOrAdapters() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, ADAPTER)
                .because("the domain is the innermost layer")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void persistenceEntitiesResideOnlyInTheOutboundPersistenceAdapter() {
        noClasses()
                .that().resideInAPackage(CONTEXT + "..")
                .and().resideOutsideOfPackage(PERSISTENCE_ADAPTER)
                .should().beAnnotatedWith(Entity.class)
                .orShould().beAnnotatedWith(Table.class)
                .because("JPA mappings belong to the outbound persistence adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void springDataRepositoriesResideOnlyInTheOutboundPersistenceAdapter() {
        noClasses()
                .that().resideInAPackage(CONTEXT + "..")
                .and().resideOutsideOfPackage(PERSISTENCE_ADAPTER)
                .should().beAssignableTo(Repository.class)
                .because("Spring Data repositories belong to the outbound persistence adapter")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void transportAnnotatedClassesResideOnlyInInboundAdapters() {
        noClasses()
                .that().resideInAPackage(CONTEXT + "..")
                .and().resideOutsideOfPackage(INBOUND_ADAPTER)
                .should().beAnnotatedWith(RestController.class)
                .orShould().beAnnotatedWith(KafkaListener.class)
                .because("transport entry points belong to adapter/in")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void transportAnnotatedMethodsResideOnlyInInboundAdapters() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage(CONTEXT + "..")
                .and().areDeclaredInClassesThat().resideOutsideOfPackage(INBOUND_ADAPTER)
                .should().beAnnotatedWith(Scheduled.class)
                .orShould().beAnnotatedWith(KafkaListener.class)
                .because("scheduled and Kafka-triggered entry points belong to adapter/in")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * {@code Infrastructure} may depend on every layer, and is itself reachable only from
     * {@code Adapter}: {@code infrastructure/config} owns the {@code @ConfigurationProperties}
     * records that the adapters read. {@code domain} and {@code application} stay cut off from
     * it, which is what ADR-001 actually requires and what the two rules above assert directly.
     */
    @Test
    void layersOnlyDependInwards() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Adapter").definedBy(ADAPTER)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
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
     */
    @Test
    void adapterServiceDoesNotDependOnFmModule() {
        noClasses()
                .that().resideInAPackage("com.wisla.fm.adapter..")
                .should().dependOnClassesThat().resideInAnyPackage("ru.wisla.fm..")
                .because("the two services stay compile-time independent (D0)")
                .check(PRODUCTION_CLASSES);
    }
}

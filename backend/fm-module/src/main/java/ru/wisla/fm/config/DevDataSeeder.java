package ru.wisla.fm.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.wisla.fm.cmdb.domain.ConfigurationItemEntity;
import ru.wisla.fm.cmdb.domain.ProductCiEntity;
import ru.wisla.fm.cmdb.domain.ProductCiId;
import ru.wisla.fm.cmdb.domain.ProductEntity;
import ru.wisla.fm.cmdb.persistence.ConfigurationItemRepository;
import ru.wisla.fm.cmdb.persistence.ProductCiRepository;
import ru.wisla.fm.cmdb.persistence.ProductRepository;
import ru.wisla.fm.configuration.domain.EventSourceEntity;
import ru.wisla.fm.configuration.persistence.EventSourceRepository;
import ru.wisla.fm.identity.domain.RoleEntity;
import ru.wisla.fm.identity.domain.UserEntity;
import ru.wisla.fm.identity.persistence.RoleRepository;
import ru.wisla.fm.identity.persistence.UserRepository;
import ru.wisla.fm.processing.domain.EventEntity;
import ru.wisla.fm.processing.persistence.EventRepository;
import ru.wisla.fm.rules.domain.ProcessingRuleEntity;
import ru.wisla.fm.rules.persistence.ProcessingRuleRepository;
import ru.wisla.fm.settings.domain.ModuleSettingsEntity;
import ru.wisla.fm.settings.persistence.ModuleSettingsRepository;

import java.time.Instant;
import java.util.Set;

@Component
public class DevDataSeeder implements ApplicationRunner {

    public static final String DEMO_SOURCE_API_KEY = "demo-source-key";
    public static final String ZABBIX_SOURCE_API_KEY = "zabbix-demo-key";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EventSourceRepository eventSourceRepository;
    private final ProcessingRuleRepository processingRuleRepository;
    private final ConfigurationItemRepository configurationItemRepository;
    private final ProductRepository productRepository;
    private final ProductCiRepository productCiRepository;
    private final EventRepository eventRepository;
    private final ModuleSettingsRepository moduleSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository,
                         RoleRepository roleRepository,
                         EventSourceRepository eventSourceRepository,
                         ProcessingRuleRepository processingRuleRepository,
                         ConfigurationItemRepository configurationItemRepository,
                         ProductRepository productRepository,
                         ProductCiRepository productCiRepository,
                         EventRepository eventRepository,
                         ModuleSettingsRepository moduleSettingsRepository,
                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.eventSourceRepository = eventSourceRepository;
        this.processingRuleRepository = processingRuleRepository;
        this.configurationItemRepository = configurationItemRepository;
        this.productRepository = productRepository;
        this.productCiRepository = productCiRepository;
        this.eventRepository = eventRepository;
        this.moduleSettingsRepository = moduleSettingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedUsers();
        seedEventSource();
        seedRules();
        seedPushDemoRuleIfAbsent();
        seedCmdb();
        seedModuleSettings();
        seedHealthSampleEvent();
    }

    private void seedUsers() {
        if (userRepository.existsByLogin("admin")) {
            return;
        }
        RoleEntity adminRole = new RoleEntity();
        adminRole.setName("Администратор");
        adminRole.setDescription("Full access MVP");
        adminRole.setPermissions("[\"console\",\"events\",\"sources\",\"rules\",\"admin\",\"settings\"]");
        adminRole.setSystemRole(true);
        roleRepository.save(adminRole);

        UserEntity admin = new UserEntity();
        admin.setLogin("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setFullName("Администратор");
        admin.setEmail("admin@wisla.local");
        admin.setTeam("NOC");
        admin.setActive(true);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);
    }

    private void seedEventSource() {
        seedDemoSource();
        seedZabbixSource();
    }

    private void seedDemoSource() {
        if (eventSourceRepository.findByWebhookPathKey("demo").isPresent()) {
            return;
        }
        EventSourceEntity source = new EventSourceEntity();
        source.setName("Demo Push REST");
        source.setType("push_rest");
        source.setProtocol("HTTPS/REST");
        source.setEndpoint("http://localhost:8081/webhook/demo");
        source.setApiKeyHash(passwordEncoder.encode(DEMO_SOURCE_API_KEY));
        source.setApiKeyPrefix("demo-****-key");
        source.setStatus("active");
        source.setWebhookPathKey("demo");
        eventSourceRepository.save(source);
    }

    private void seedZabbixSource() {
        if (eventSourceRepository.findByWebhookPathKey("zabbix-prod-01").isPresent()) {
            return;
        }
        EventSourceEntity source = new EventSourceEntity();
        source.setName("Zabbix Main (simulator)");
        source.setType("push_rest");
        source.setProtocol("HTTPS/REST");
        source.setEndpoint("http://localhost:8081/webhook/zabbix-prod-01");
        source.setApiKeyHash(passwordEncoder.encode(ZABBIX_SOURCE_API_KEY));
        source.setApiKeyPrefix("zabbix-****-key");
        source.setStatus("active");
        source.setWebhookPathKey("zabbix-prod-01");
        source.setAdapterVersion("6.4.0");
        eventSourceRepository.save(source);
    }

    private void seedRules() {
        if (!processingRuleRepository.findAll().isEmpty()) {
            return;
        }
        ProcessingRuleEntity dedup = new ProcessingRuleEntity();
        dedup.setName("Дедупликация по источнику+заголовок+CI");
        dedup.setRuleType("dedup");
        dedup.setEnabled(true);
        dedup.setTriggerType("Событие потока");
        dedup.setApprovalStatus("approved");
        dedup.setDescription("Merge by source_id + title + ci_id (repeat_count)");
        dedup.setCanvas("""
                {"nodes":[
                  {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
                  {"id":"b4","type":"dedup","config":{"key":"source_id + title + ci_id"}}
                ],"edges":[
                  {"id":"e1","source":"b1","target":"b4"}
                ]}
                """);
        processingRuleRepository.save(dedup);

        ProcessingRuleEntity threshold = new ProcessingRuleEntity();
        threshold.setName("5 critical за 10 минут");
        threshold.setRuleType("threshold");
        threshold.setEnabled(true);
        threshold.setTriggerType("Событие FM");
        threshold.setApprovalStatus("approved");
        threshold.setDescription("5 critical events in 10 minutes → synthetic fatal event");
        threshold.setCanvas("""
                {"nodes":[
                  {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
                  {"id":"b5","type":"threshold","config":{"count":"5","windowMin":"10"}}
                ],"edges":[
                  {"id":"e1","source":"b1","target":"b5"}
                ]}
                """);
        processingRuleRepository.save(threshold);

        ProcessingRuleEntity correlation = new ProcessingRuleEntity();
        correlation.setName("Корреляция: 2 события за 10 мин");
        correlation.setRuleType("correlation");
        correlation.setEnabled(false);
        correlation.setTriggerType("Событие потока");
        correlation.setApprovalStatus("approved");
        correlation.setDescription("Link 2 matching events within 10 minutes as root/child");
        correlation.setCanvas("""
                {"nodes":[
                  {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
                  {"id":"b7","type":"correlation","config":{"count":"2","windowMin":"10","matchField":"title"}}
                ],"edges":[
                  {"id":"e1","source":"b1","target":"b7"}
                ]}
                """);
        processingRuleRepository.save(correlation);

        ProcessingRuleEntity pushDemo = new ProcessingRuleEntity();
        pushDemo.setName("Push: critical → оператору");
        pushDemo.setRuleType("threshold");
        pushDemo.setEnabled(true);
        pushDemo.setTriggerType("Событие потока");
        pushDemo.setApprovalStatus("approved");
        pushDemo.setDescription("In-app push when critical event arrives");
        pushDemo.setCanvas("""
                {"nodes":[
                  {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
                  {"id":"b2","type":"condition","config":{"field":"severity","operator":"eq","value":"critical"}},
                  {"id":"b8","type":"push","config":{"message":"Critical: {title}"}}
                ],"edges":[
                  {"id":"e1","source":"b1","target":"b2"},
                  {"id":"e2","source":"b2","target":"b8"}
                ]}
                """);
        processingRuleRepository.save(pushDemo);
    }

    private void seedPushDemoRuleIfAbsent() {
        boolean exists = processingRuleRepository.findAll().stream()
                .anyMatch(rule -> rule.getName() != null && rule.getName().startsWith("Push:"));
        if (exists) {
            return;
        }
        ProcessingRuleEntity pushDemo = new ProcessingRuleEntity();
        pushDemo.setName("Push: critical → оператору");
        pushDemo.setRuleType("threshold");
        pushDemo.setEnabled(true);
        pushDemo.setTriggerType("Событие потока");
        pushDemo.setApprovalStatus("approved");
        pushDemo.setDescription("In-app push when critical event arrives");
        pushDemo.setCanvas("""
                {"nodes":[
                  {"id":"b1","type":"trigger","config":{"triggerType":"stream"}},
                  {"id":"b2","type":"condition","config":{"field":"severity","operator":"eq","value":"critical"}},
                  {"id":"b8","type":"push","config":{"message":"Critical: {title}"}}
                ],"edges":[
                  {"id":"e1","source":"b1","target":"b2"},
                  {"id":"e2","source":"b2","target":"b8"}
                ]}
                """);
        processingRuleRepository.save(pushDemo);
    }

    private void seedCmdb() {
        if (configurationItemRepository.findByFqdn("demo-server.wisla.local").isPresent()) {
            return;
        }
        ConfigurationItemEntity ci = new ConfigurationItemEntity();
        ci.setFqdn("demo-server.wisla.local");
        ci.setCiType("node");
        ci.setSystemName("Demo Billing");
        ci.setSubsystemName("API");
        ci.setAutoCreated(false);
        configurationItemRepository.save(ci);

        ProductEntity product = new ProductEntity();
        product.setCode("prod-billing");
        product.setName("Billing Platform");
        product.setTenant("moscow");
        product.setSite("dc1");
        product.setTags("[\"billing\"]");
        product.setMaxSeverity("normal");
        product.setActiveEventCount(0);
        productRepository.save(product);

        ProductCiEntity link = new ProductCiEntity();
        link.setId(new ProductCiId(product.getId(), ci.getId()));
        productCiRepository.save(link);
    }

    private void seedModuleSettings() {
        if (moduleSettingsRepository.findBySettingsKey("default").isPresent()) {
            return;
        }
        moduleSettingsRepository.save(new ModuleSettingsEntity());
    }

    private void seedHealthSampleEvent() {
        if (!eventRepository.findAll().isEmpty()) {
            return;
        }
        ConfigurationItemEntity ci = configurationItemRepository.findByFqdn("demo-server.wisla.local").orElse(null);
        EventSourceEntity source = eventSourceRepository.findAll().stream().findFirst().orElse(null);
        if (ci == null || source == null) {
            return;
        }
        EventEntity event = new EventEntity();
        event.setStatus("new");
        event.setSeverity("major");
        event.setTitle("Demo health degradation");
        event.setDescription("Seeded active event for product health aggregation");
        event.setSourceId(source.getId());
        event.setCiId(ci.getId());
        event.setNodeFqdn(ci.getFqdn());
        event.setSystemName(ci.getSystemName());
        event.setSubsystemName(ci.getSubsystemName());
        event.setSourceAt(Instant.now());
        eventRepository.save(event);
    }
}

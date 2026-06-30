import {
  ConfigurationItem,
  DashboardSummary,
  Event,
  EventMap,
  EventSource,
  ProcessingRule,
  ProductHealth,
  RawEvent,
  Role,
  SettingsBundle,
  User,
} from '../api/api.models';

export const MOCK_USERS: User[] = [
  {
    id: '00000000-0000-4000-8000-000000000001',
    login: 'operator',
    fullName: 'Иванов А.С.',
    email: 'operator@wisla.local',
    roleIds: ['00000000-0000-4000-8000-000000000010'],
    team: 'NOC Центр',
    active: true,
  },
  {
    id: '00000000-0000-4000-8000-000000000002',
    login: 'admin',
    fullName: 'Петров В.И.',
    email: 'admin@wisla.local',
    roleIds: ['00000000-0000-4000-8000-000000000011'],
    team: 'Администрирование',
    active: true,
  },
];

export const MOCK_ROLES: Role[] = [
  {
    id: '00000000-0000-4000-8000-000000000010',
    name: 'Дежурный',
    description: 'Консоль и здоровье продуктов',
    permissions: ['console', 'events', 'settings:profile'],
  },
  {
    id: '00000000-0000-4000-8000-000000000011',
    name: 'Администратор',
    description: 'Полный доступ',
    permissions: ['console', 'events', 'sources', 'rules', 'admin', 'settings'],
  },
];

export const MOCK_PRODUCTS: ProductHealth[] = [
  {
    id: '10000000-0000-4000-8000-000000000001',
    name: 'Платёжный шлюз',
    tenant: 'Банк',
    site: 'DC-1',
    maxSeverity: 'critical',
    activeEventCount: 3,
    ciIds: ['20000000-0000-4000-8000-000000000001'],
    tags: ['payments', 'tier-1'],
  },
  {
    id: '10000000-0000-4000-8000-000000000002',
    name: 'CRM Core',
    tenant: 'Банк',
    site: 'DC-2',
    maxSeverity: 'major',
    activeEventCount: 1,
    ciIds: ['20000000-0000-4000-8000-000000000002'],
    tags: ['crm'],
  },
  {
    id: '10000000-0000-4000-8000-000000000003',
    name: 'Auth Service',
    tenant: 'Банк',
    site: 'DC-1',
    maxSeverity: 'warning',
    activeEventCount: 0,
    ciIds: ['20000000-0000-4000-8000-000000000003'],
    tags: ['auth'],
  },
];

export const MOCK_MAPS: EventMap[] = [
  {
    id: '30000000-0000-4000-8000-000000000001',
    name: 'Активные',
    query: 'status != closed',
    isSystem: true,
    isPersonal: false,
    columns: ['status', 'severity', 'title', 'nodeFqdn', 'createdAt'],
  },
  {
    id: '30000000-0000-4000-8000-000000000002',
    name: 'Critical/Major',
    query: 'severity in (critical, major)',
    isSystem: true,
    isPersonal: false,
    columns: ['severity', 'title', 'systemName', 'repeatCount'],
  },
];

export const MOCK_DASHBOARD: DashboardSummary = {
  severityCounts: { fatal: 0, critical: 2, major: 4, minor: 7, warning: 12, normal: 0 },
  totalActive: 25,
  productPreview: MOCK_PRODUCTS,
  systemMaps: MOCK_MAPS,
};

export const MOCK_EVENTS: Event[] = [
  {
    id: '40000000-0000-4000-8000-000000000001',
    status: 'new',
    severity: 'critical',
    title: 'High CPU on payment-db-01',
    description: 'CPU usage above 95% for 5 minutes',
    repeatCount: 3,
    ciId: '20000000-0000-4000-8000-000000000001',
    nodeFqdn: 'payment-db-01.bank.local',
    systemName: 'Платёжный шлюз',
    sourceId: '50000000-0000-4000-8000-000000000001',
    sourceName: 'Zabbix Push',
    createdAt: new Date().toISOString(),
    sourceAt: new Date().toISOString(),
  },
  {
    id: '40000000-0000-4000-8000-000000000002',
    status: 'in_progress',
    severity: 'major',
    title: 'Disk space low on crm-app-02',
    description: '/var below 10%',
    assignedUserName: 'Иванов А.С.',
    repeatCount: 1,
    ciId: '20000000-0000-4000-8000-000000000002',
    nodeFqdn: 'crm-app-02.bank.local',
    systemName: 'CRM Core',
    sourceId: '50000000-0000-4000-8000-000000000001',
    createdAt: new Date(Date.now() - 3600000).toISOString(),
    sourceAt: new Date(Date.now() - 3600000).toISOString(),
  },
];

export const MOCK_RAW_EVENTS: RawEvent[] = [
  {
    id: '60000000-0000-4000-8000-000000000001',
    sourceId: '50000000-0000-4000-8000-000000000001',
    sourceName: 'Zabbix Push',
    title: 'Raw: CPU trigger fired',
    severity: 'critical',
    processed: false,
    externalId: 'zbx-12345',
    nodeFqdn: 'payment-db-01.bank.local',
    createdAt: new Date().toISOString(),
    occurredAt: new Date().toISOString(),
  },
];

export const MOCK_SOURCES: EventSource[] = [
  {
    id: '50000000-0000-4000-8000-000000000001',
    name: 'Zabbix Push',
    type: 'push_rest',
    status: 'active',
    endpoint: 'http://localhost:8081/webhook/zabbix-main',
    apiKeyMasked: 'zbx-****-key',
    adapterVersion: '1.0.0',
    lastSuccessAt: new Date().toISOString(),
  },
  {
    id: '50000000-0000-4000-8000-000000000002',
    name: 'vCenter Pull',
    type: 'pull_etl',
    status: 'inactive',
    schedule: '0 */15 * * * *',
    adapterVersion: '1.0.0',
    lastSuccessAt: new Date(Date.now() - 86400000).toISOString(),
  },
];

export const MOCK_RULES: ProcessingRule[] = [
  {
    id: '70000000-0000-4000-8000-000000000001',
    name: 'Дедуп по externalId',
    ruleType: 'dedup',
    enabled: true,
    triggerType: 'Событие потока',
    lastRunAt: new Date().toISOString(),
    approvalStatus: 'approved',
    description: 'Схлопывание повторов за 15 мин',
  },
  {
    id: '70000000-0000-4000-8000-000000000002',
    name: 'Problem → Resolution',
    ruleType: 'problem_resolution',
    enabled: false,
    triggerType: 'Событие FM',
    approvalStatus: 'pending',
    description: 'Автозакрытие recovery-событий',
  },
];

export const MOCK_CI: ConfigurationItem[] = [
  {
    id: '20000000-0000-4000-8000-000000000001',
    fqdn: 'payment-db-01.bank.local',
    ciType: 'database',
    system: 'Платёжный шлюз',
    subsystem: 'DB',
    software: 'PostgreSQL 15',
    products: ['10000000-0000-4000-8000-000000000001'],
    tags: ['tier-1', 'postgres'],
  },
];

export const MOCK_SETTINGS: SettingsBundle = {
  module: {
    timezone: 'Europe/Moscow',
    pollingIntervalSec: 60,
    autoArchiveDays: 90,
    repeatIntervalMin: 15,
    wislaIntegration: false,
    itsmIntegration: false,
  },
  profile: { sidebarCollapsed: false },
};

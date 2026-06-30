export type Severity = 'fatal' | 'critical' | 'major' | 'minor' | 'warning' | 'normal';
export type EventStatus = 'new' | 'in_progress' | 'closed' | 'archived' | 'maintenance' | 'deferred';
export type SourceType = 'push_rest' | 'push_snmp_trap' | 'pull_etl';
export type SourceStatus = 'active' | 'inactive' | 'blocked';
export type RuleType = 'dedup' | 'problem_resolution' | 'correlation' | 'threshold';

export interface User {
  id: string;
  login: string;
  fullName: string;
  email: string;
  roleIds: string[];
  team: string;
  active: boolean;
}

export interface Role {
  id: string;
  name: string;
  description: string;
  permissions: string[];
}

export interface ConfigurationItem {
  id: string;
  fqdn: string;
  ciType: string;
  system: string;
  subsystem: string;
  software: string;
  products: string[];
  tags: string[];
}

export interface EventSource {
  id: string;
  name: string;
  type: SourceType;
  protocol: string;
  endpoint: string;
  apiKey: string;
  adapterVersion: string;
  lastSuccessAt: string;
  status: SourceStatus;
  schedule?: string;
}

export interface ProcessingRule {
  id: string;
  name: string;
  ruleType: RuleType;
  enabled: boolean;
  triggerType: string;
  lastRunAt: string;
  approvalStatus: 'approved' | 'pending' | 'draft';
  description: string;
}

export interface EventActionLog {
  id: string;
  eventId: string;
  action: string;
  userName: string;
  timestamp: string;
  details: string;
}

export interface Event {
  id: string;
  status: EventStatus;
  severity: Severity;
  title: string;
  description: string;
  assignedUserId?: string;
  repeatCount: number;
  ciId: string;
  nodeFqdn: string;
  systemName: string;
  sourceId: string;
  rootEventId?: string;
  childEventIds?: string[];
  itsmIncidentNumber?: string;
  createdAt: string;
  sourceAt: string;
  lastRepeatAt?: string;
  takenAt?: string;
  closedAt?: string;
  isRaw?: boolean;
}

export interface ProductHealth {
  id: string;
  name: string;
  tenant: string;
  site: string;
  maxSeverity: Severity;
  activeEventCount: number;
  ciIds: string[];
  tags: string[];
}

export interface EventMap {
  id: string;
  name: string;
  query: string;
  isSystem: boolean;
  isPersonal: boolean;
  columns: string[];
  unsaved?: boolean;
}

export interface Settings {
  timezone: string;
  pollingIntervalSec: number;
  autoArchiveDays: number;
  repeatIntervalMin: number;
  wislaIntegration: boolean;
  itsmIntegration: boolean;
}

export type HealthLevel = 'fatal' | 'critical' | 'major' | 'warning' | 'ok' | 'unknown';
export type InfluenceType = 'combo' | 'weighted' | 'critical';

export interface CiComponentHealth {
  id: string;
  name: string;
  health: HealthLevel;
  healthPercent: number;
  damageFromComponents: number;
  damageFromInfluence: number;
}

export interface DependentCi {
  id: string;
  fqdn: string;
  componentName?: string;
  health: HealthLevel;
  healthPercent: number;
  influenceType: InfluenceType;
}

export interface CiHealthProfile {
  ciId: string;
  currentHealth: HealthLevel;
  healthPercent: number;
  minToday: HealthLevel;
  minTodayPercent: number;
  maxToday: HealthLevel;
  maxTodayPercent: number;
  components: CiComponentHealth[];
  dependents: DependentCi[];
  signalsBySeverity: Record<Severity, number>;
  timeline: { time: string; percent: number; level: HealthLevel }[];
}

export const currentUser: User = {
  id: 'u-001',
  login: 'ivanov',
  fullName: 'Иванов А.С.',
  email: 'ivanov@company.ru',
  roleIds: ['role-admin'],
  team: 'NOC Центр',
  active: true,
};

export const users: User[] = [
  currentUser,
  { id: 'u-002', login: 'petrov', fullName: 'Петров В.И.', email: 'petrov@company.ru', roleIds: ['role-specialist'], team: 'NOC Центр', active: true },
  { id: 'u-003', login: 'sidorova', fullName: 'Сидорова М.К.', email: 'sidorova@company.ru', roleIds: ['role-duty'], team: 'NOC Регион', active: true },
  { id: 'u-004', login: 'kozlov', fullName: 'Козлов Д.П.', email: 'kozlov@company.ru', roleIds: ['role-approver'], team: 'Аналитика', active: true },
  { id: 'u-005', login: 'service-adapter', fullName: 'Сервис адаптера', email: 'adapter@company.ru', roleIds: ['role-service'], team: 'Системные', active: true },
];

export const roles: Role[] = [
  { id: 'role-admin', name: 'Администратор', description: 'Полный доступ к настройкам модуля', permissions: ['console', 'events', 'sources', 'rules', 'admin', 'settings'] },
  { id: 'role-specialist', name: 'Специалист мониторинга', description: 'Работа с событиями и правилами', permissions: ['console', 'events', 'sources:read', 'rules', 'settings:profile'] },
  { id: 'role-duty', name: 'Дежурный инженер', description: 'Оператор консоли', permissions: ['console', 'events'] },
  { id: 'role-approver', name: 'Согласующий правил', description: 'Утверждение правил корреляции', permissions: ['rules:approve'] },
  { id: 'role-service', name: 'Сервисная УЗ', description: 'API-интеграции', permissions: ['api:ingest', 'api:events'] },
];

export const configurationItems: ConfigurationItem[] = [
  { id: 'ci-001', fqdn: 'db-prod-01.moscow.company.ru', ciType: 'узел', system: 'БД Oracle', subsystem: 'Кластер RAC', software: 'Oracle 19c', products: ['prod-billing'], tags: ['tenant:moscow', 'site:dc1', 'criticality:high'] },
  { id: 'ci-002', fqdn: 'web-app-03.moscow.company.ru', ciType: 'узел', system: 'Веб-портал', subsystem: 'Frontend', software: 'Nginx 1.24', products: ['prod-portal'], tags: ['tenant:moscow', 'site:dc1'] },
  { id: 'ci-003', fqdn: 'k8s-worker-12.spb.company.ru', ciType: 'узел', system: 'Kubernetes', subsystem: 'Worker', software: 'K8s 1.29', products: ['prod-analytics'], tags: ['tenant:spb', 'site:dc2'] },
  { id: 'ci-004', fqdn: 'switch-core-01.moscow.company.ru', ciType: 'оборудование', system: 'Сеть', subsystem: 'Core', software: 'Cisco NX-OS', products: ['prod-network'], tags: ['tenant:moscow', 'site:dc1', 'criticality:critical'] },
  { id: 'ci-005', fqdn: 'zabbix-proxy-02.company.ru', ciType: 'сервис', system: 'Мониторинг', subsystem: 'Zabbix Proxy', software: 'Zabbix 6.4', products: ['prod-monitoring'], tags: ['tenant:global'] },
  { id: 'ci-006', fqdn: 'kafka-broker-01.spb.company.ru', ciType: 'узел', system: 'Kafka', subsystem: 'Broker', software: 'Kafka 3.6', products: ['prod-streaming'], tags: ['tenant:spb', 'site:dc2'] },
];

export const sources: EventSource[] = [
  { id: 'src-001', name: 'Zabbix Push — Москва DC1', type: 'push_rest', protocol: 'HTTPS/REST', endpoint: 'https://adapter.company.ru/zabbix-moscow', apiKey: 'zk-****-mos-7f3a', adapterVersion: '1.2.0', lastSuccessAt: '2026-06-22T14:55:00+03:00', status: 'active' },
  { id: 'src-002', name: 'AlertManager — K8s кластер', type: 'push_rest', protocol: 'HTTPS/REST', endpoint: 'https://adapter.company.ru/alertmanager-k8s', apiKey: 'am-****-k8s-9b2c', adapterVersion: '1.2.0', lastSuccessAt: '2026-06-22T14:58:30+03:00', status: 'active' },
  { id: 'src-003', name: 'SNMP Trap — сеть Core', type: 'push_snmp_trap', protocol: 'SNMP v3', endpoint: 'trap-receiver.company.ru:162', apiKey: 'snmp-****-net-4d1e', adapterVersion: '1.1.5', lastSuccessAt: '2026-06-22T14:50:12+03:00', status: 'inactive' },
];

export const rules: ProcessingRule[] = [
  { id: 'rule-001', name: 'Дедупликация по source+title+CI', ruleType: 'dedup', enabled: true, triggerType: 'Событие потока', lastRunAt: '2026-06-22T14:59:00+03:00', approvalStatus: 'approved', description: 'Слияние повторных событий с инкрементом repeat_count' },
  { id: 'rule-002', name: 'Порог: 5 Critical за 10 мин', ruleType: 'threshold', enabled: true, triggerType: 'Событие FM', lastRunAt: '2026-06-22T14:45:00+03:00', approvalStatus: 'approved', description: 'Создание синтетического события при превышении порога' },
  { id: 'rule-003', name: 'Problem–Resolution: доступность порта', ruleType: 'problem_resolution', enabled: true, triggerType: 'Событие потока', lastRunAt: '2026-06-22T13:30:00+03:00', approvalStatus: 'approved', description: 'Закрытие активной аварии по closing message' },
];

export const products: ProductHealth[] = [
  { id: 'prod-billing', name: 'Биллинг', tenant: 'Москва', site: 'DC1', maxSeverity: 'critical', activeEventCount: 3, ciIds: ['ci-001'], tags: ['финансы'] },
  { id: 'prod-portal', name: 'Клиентский портал', tenant: 'Москва', site: 'DC1', maxSeverity: 'major', activeEventCount: 2, ciIds: ['ci-002'], tags: ['внешний'] },
  { id: 'prod-analytics', name: 'Аналитическая платформа', tenant: 'СПб', site: 'DC2', maxSeverity: 'warning', activeEventCount: 1, ciIds: ['ci-003', 'ci-006'], tags: ['bigdata'] },
  { id: 'prod-network', name: 'Сетевая инфраструктура', tenant: 'Москва', site: 'DC1', maxSeverity: 'fatal', activeEventCount: 4, ciIds: ['ci-004'], tags: ['инфра'] },
  { id: 'prod-monitoring', name: 'Мониторинг', tenant: 'Глобальный', site: 'DC1', maxSeverity: 'minor', activeEventCount: 1, ciIds: ['ci-005'], tags: ['внутренний'] },
  { id: 'prod-streaming', name: 'Потоковая обработка', tenant: 'СПб', site: 'DC2', maxSeverity: 'normal', activeEventCount: 0, ciIds: ['ci-006'], tags: ['kafka'] },
  { id: 'prod-crm', name: 'CRM', tenant: 'Москва', site: 'DC1', maxSeverity: 'normal', activeEventCount: 0, ciIds: [], tags: ['продажи'] },
  { id: 'prod-hr', name: 'HR-портал', tenant: 'Москва', site: 'DC2', maxSeverity: 'normal', activeEventCount: 0, ciIds: [], tags: ['внутренний'] },
];

export const eventMaps: EventMap[] = [
  { id: 'map-active', name: 'Активные', query: 'status != closed', isSystem: true, isPersonal: false, columns: ['status', 'severity', 'title', 'nodeFqdn', 'systemName', 'sourceId', 'createdAt', 'assignedUserId', 'repeatCount'] },
  { id: 'map-closed', name: 'Закрытые', query: 'status = closed', isSystem: true, isPersonal: false, columns: ['status', 'severity', 'title', 'nodeFqdn', 'closedAt', 'repeatCount'] },
  { id: 'map-critical', name: 'Critical/Major', query: 'severity IN (critical, major)', isSystem: false, isPersonal: true, columns: ['severity', 'title', 'nodeFqdn', 'createdAt'], unsaved: true },
];

export const actionLogs: EventActionLog[] = [
  { id: 'log-001', eventId: 'evt-001', action: 'Создание', userName: 'Система', timestamp: '2026-06-22T14:30:00+03:00', details: 'Событие получено от адаптера Zabbix' },
  { id: 'log-002', eventId: 'evt-001', action: 'Дедупликация', userName: 'Система', timestamp: '2026-06-22T14:32:00+03:00', details: 'repeat_count увеличен до 3' },
  { id: 'log-003', eventId: 'evt-002', action: 'Принятие в работу', userName: 'Петров В.И.', timestamp: '2026-06-22T14:35:00+03:00', details: 'Статус изменён: Новый → В работе' },
  { id: 'log-004', eventId: 'evt-005', action: 'Комментарий', userName: 'Иванов А.С.', timestamp: '2026-06-22T14:40:00+03:00', details: 'Эскалация на сетевую команду' },
  { id: 'log-005', eventId: 'evt-008', action: 'Корреляция', userName: 'Система', timestamp: '2026-06-22T14:42:00+03:00', details: 'Связано с корневым событием evt-005' },
];

export const events: Event[] = [
  { id: 'evt-001', status: 'new', severity: 'critical', title: 'Недоступность экземпляра Oracle RAC', description: 'Instance db-prod-01 недоступен, ошибка подключения', repeatCount: 3, ciId: 'ci-001', nodeFqdn: 'db-prod-01.moscow.company.ru', systemName: 'БД Oracle', sourceId: 'src-001', createdAt: '2026-06-22T14:30:00+03:00', sourceAt: '2026-06-22T14:29:55+03:00', lastRepeatAt: '2026-06-22T14:32:00+03:00' },
  { id: 'evt-002', status: 'in_progress', severity: 'major', title: 'Высокая загрузка CPU на web-app-03', description: 'CPU > 90% в течение 5 минут', assignedUserId: 'u-002', repeatCount: 1, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-001', createdAt: '2026-06-22T14:25:00+03:00', sourceAt: '2026-06-22T14:24:50+03:00', takenAt: '2026-06-22T14:35:00+03:00', itsmIncidentNumber: 'INC-2026-004521' },
  { id: 'evt-003', status: 'new', severity: 'fatal', title: 'Потеря связности Core Switch', description: 'switch-core-01 не отвечает на ICMP и SNMP', repeatCount: 1, ciId: 'ci-004', nodeFqdn: 'switch-core-01.moscow.company.ru', systemName: 'Сеть', sourceId: 'src-003', createdAt: '2026-06-22T14:50:00+03:00', sourceAt: '2026-06-22T14:49:58+03:00' },
  { id: 'evt-004', status: 'new', severity: 'warning', title: 'Дисковое пространство < 15%', description: '/var на k8s-worker-12 заполнен на 87%', repeatCount: 1, ciId: 'ci-003', nodeFqdn: 'k8s-worker-12.spb.company.ru', systemName: 'Kubernetes', sourceId: 'src-002', createdAt: '2026-06-22T14:20:00+03:00', sourceAt: '2026-06-22T14:19:45+03:00' },
  { id: 'evt-005', status: 'in_progress', severity: 'critical', title: 'BGP сессия DOWN на uplink', description: 'Потеря BGP пира 10.0.0.1', assignedUserId: 'u-001', repeatCount: 1, ciId: 'ci-004', nodeFqdn: 'switch-core-01.moscow.company.ru', systemName: 'Сеть', sourceId: 'src-003', childEventIds: ['evt-008'], createdAt: '2026-06-22T14:10:00+03:00', sourceAt: '2026-06-22T14:09:55+03:00', takenAt: '2026-06-22T14:15:00+03:00' },
  { id: 'evt-006', status: 'closed', severity: 'minor', title: 'Задержка ответа Zabbix Proxy', description: 'Время отклика > 2 сек', repeatCount: 2, ciId: 'ci-005', nodeFqdn: 'zabbix-proxy-02.company.ru', systemName: 'Мониторинг', sourceId: 'src-001', createdAt: '2026-06-22T13:00:00+03:00', sourceAt: '2026-06-22T12:59:50+03:00', closedAt: '2026-06-22T13:45:00+03:00' },
  { id: 'evt-007', status: 'new', severity: 'major', title: 'Pod CrashLoopBackOff', description: 'analytics-worker в namespace prod-analytics', repeatCount: 5, ciId: 'ci-003', nodeFqdn: 'k8s-worker-12.spb.company.ru', systemName: 'Kubernetes', sourceId: 'src-002', createdAt: '2026-06-22T14:40:00+03:00', sourceAt: '2026-06-22T14:39:30+03:00', lastRepeatAt: '2026-06-22T14:48:00+03:00' },
  { id: 'evt-008', status: 'new', severity: 'major', title: 'Недоступность сервисов за Core Switch', description: 'Симптоматическое событие', repeatCount: 1, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-001', rootEventId: 'evt-005', createdAt: '2026-06-22T14:12:00+03:00', sourceAt: '2026-06-22T14:11:55+03:00' },
  { id: 'evt-009', status: 'new', severity: 'normal', title: 'Плановый перезапуск сервиса', description: 'Рестарт nginx по расписанию', repeatCount: 1, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-001', createdAt: '2026-06-22T14:00:00+03:00', sourceAt: '2026-06-22T14:00:00+03:00' },
  { id: 'evt-010', status: 'closed', severity: 'critical', title: 'Недоступность порта 443', description: 'Порт закрыт на web-app-03', repeatCount: 1, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-001', createdAt: '2026-06-22T12:00:00+03:00', sourceAt: '2026-06-22T11:59:50+03:00', closedAt: '2026-06-22T12:30:00+03:00' },
  { id: 'evt-011', status: 'new', severity: 'minor', title: 'Рост latency Kafka consumer', description: 'Consumer lag > 10000 сообщений', repeatCount: 1, ciId: 'ci-006', nodeFqdn: 'kafka-broker-01.spb.company.ru', systemName: 'Kafka', sourceId: 'src-002', createdAt: '2026-06-22T14:45:00+03:00', sourceAt: '2026-06-22T14:44:50+03:00' },
  { id: 'evt-012', status: 'deferred', severity: 'warning', title: 'Истечение SSL-сертификата через 7 дней', description: 'Сертификат portal.company.ru', assignedUserId: 'u-003', repeatCount: 1, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-001', createdAt: '2026-06-22T10:00:00+03:00', sourceAt: '2026-06-22T10:00:00+03:00', takenAt: '2026-06-22T10:30:00+03:00' },
  { id: 'evt-013', status: 'new', severity: 'critical', title: 'OOM Killer на db-prod-01', description: 'Процесс oracle убит OOM killer', repeatCount: 1, ciId: 'ci-001', nodeFqdn: 'db-prod-01.moscow.company.ru', systemName: 'БД Oracle', sourceId: 'src-001', createdAt: '2026-06-22T14:52:00+03:00', sourceAt: '2026-06-22T14:51:55+03:00' },
  { id: 'evt-014', status: 'maintenance', severity: 'normal', title: 'Обслуживание: патч ОС', description: 'Плановые работы на k8s-worker-12', repeatCount: 1, ciId: 'ci-003', nodeFqdn: 'k8s-worker-12.spb.company.ru', systemName: 'Kubernetes', sourceId: 'src-002', createdAt: '2026-06-22T08:00:00+03:00', sourceAt: '2026-06-22T08:00:00+03:00' },
  { id: 'evt-015', status: 'new', severity: 'warning', title: 'Превышение порога ошибок 5xx', description: 'Error rate > 5% на /api/v1/orders', repeatCount: 4, ciId: 'ci-002', nodeFqdn: 'web-app-03.moscow.company.ru', systemName: 'Веб-портал', sourceId: 'src-002', createdAt: '2026-06-22T14:38:00+03:00', sourceAt: '2026-06-22T14:37:50+03:00', lastRepeatAt: '2026-06-22T14:47:00+03:00' },
  { id: 'evt-016', status: 'archived', severity: 'major', title: 'Недоступность реплики БД', description: 'Standby отстаёт на 30 мин', repeatCount: 1, ciId: 'ci-001', nodeFqdn: 'db-prod-01.moscow.company.ru', systemName: 'БД Oracle', sourceId: 'src-001', createdAt: '2026-06-21T18:00:00+03:00', sourceAt: '2026-06-21T17:59:50+03:00', closedAt: '2026-06-21T20:00:00+03:00' },
];

export const rawEvents: Event[] = events.map((e, i) => ({
  ...e,
  id: `raw-${e.id}`,
  isRaw: true,
  title: i < 3 ? `[RAW] ${e.title}` : e.title,
}));

export const settings: Settings = {
  timezone: 'Europe/Moscow',
  pollingIntervalSec: 60,
  autoArchiveDays: 30,
  repeatIntervalMin: 15,
  wislaIntegration: false,
  itsmIntegration: false,
};

export function getUserName(userId?: string): string {
  if (!userId) return '—';
  return users.find((u) => u.id === userId)?.fullName ?? userId;
}

export function getSourceName(sourceId: string): string {
  return sources.find((s) => s.id === sourceId)?.name ?? sourceId;
}

export function getCi(ciId: string): ConfigurationItem | undefined {
  return configurationItems.find((c) => c.id === ciId);
}

export function getEventById(id: string): Event | undefined {
  return events.find((e) => e.id === id);
}

export function getProductById(id: string): ProductHealth | undefined {
  return products.find((p) => p.id === id);
}

export function getSourceById(id: string): EventSource | undefined {
  return sources.find((s) => s.id === id);
}

export function getRuleById(id: string): ProcessingRule | undefined {
  return rules.find((r) => r.id === id);
}

export function getActionLogsForEvent(eventId: string): EventActionLog[] {
  return actionLogs.filter((l) => l.eventId === eventId);
}

export function countBySeverity(severities: Severity[]): Record<Severity, number> {
  const counts: Record<Severity, number> = { fatal: 0, critical: 0, major: 0, minor: 0, warning: 0, normal: 0 };
  const active = events.filter((e) => e.status !== 'closed' && e.status !== 'archived');
  for (const e of active) {
    if (severities.includes(e.severity)) counts[e.severity]++;
  }
  return counts;
}

export const severityLabels: Record<Severity, string> = {
  fatal: 'Fatal',
  critical: 'Critical',
  major: 'Major',
  minor: 'Minor',
  warning: 'Warning',
  normal: 'Normal',
};

export const statusLabels: Record<EventStatus, string> = {
  new: 'Новый',
  in_progress: 'В работе',
  closed: 'Закрыт',
  archived: 'Архив',
  maintenance: 'Обслуживание',
  deferred: 'Отложен',
};

export const healthLabels: Record<HealthLevel, string> = {
  fatal: 'Fatal',
  critical: 'Critical',
  major: 'Major',
  warning: 'Warning',
  ok: 'Ok',
  unknown: 'Unknown',
};

const timeline24h = (base: number, dips: number[]): CiHealthProfile['timeline'] => {
  const levels: HealthLevel[] = ['ok', 'ok', 'warning', 'major', 'critical', 'major', 'warning', 'ok', 'ok', 'warning', 'major', 'ok'];
  return dips.map((percent, i) => ({
    time: `${String(i * 2).padStart(2, '0')}:00`,
    percent,
    level: levels[i] ?? (percent > 70 ? 'ok' : percent > 40 ? 'warning' : 'critical'),
  }));
};

export const ciHealthProfiles: Record<string, CiHealthProfile> = {
  'ci-004': {
    ciId: 'ci-004',
    currentHealth: 'fatal',
    healthPercent: 18,
    minToday: 'fatal',
    minTodayPercent: 12,
    maxToday: 'warning',
    maxTodayPercent: 78,
    components: [
      { id: 'cmp-1', name: 'Интерфейсы uplink', health: 'fatal', healthPercent: 10, damageFromComponents: 45, damageFromInfluence: 35 },
      { id: 'cmp-2', name: 'BGP / маршрутизация', health: 'critical', healthPercent: 22, damageFromComponents: 30, damageFromInfluence: 28 },
      { id: 'cmp-3', name: 'Питание / PSU', health: 'ok', healthPercent: 95, damageFromComponents: 2, damageFromInfluence: 0 },
    ],
    dependents: [
      { id: 'ci-002', fqdn: 'web-app-03.moscow.company.ru', health: 'major', healthPercent: 55, influenceType: 'critical' },
      { id: 'ci-001', fqdn: 'db-prod-01.moscow.company.ru', componentName: 'Сеть', health: 'critical', healthPercent: 40, influenceType: 'weighted' },
    ],
    signalsBySeverity: { fatal: 1, critical: 2, major: 1, minor: 0, warning: 0, normal: 0 },
    timeline: timeline24h(18, [78, 75, 70, 55, 40, 28, 22, 18, 20, 25, 18, 18]),
  },
  'ci-001': {
    ciId: 'ci-001',
    currentHealth: 'critical',
    healthPercent: 35,
    minToday: 'critical',
    minTodayPercent: 28,
    maxToday: 'ok',
    maxTodayPercent: 92,
    components: [
      { id: 'cmp-1', name: 'Инстанс RAC', health: 'critical', healthPercent: 30, damageFromComponents: 50, damageFromInfluence: 15 },
      { id: 'cmp-2', name: 'Дисковая подсистема', health: 'major', healthPercent: 48, damageFromComponents: 25, damageFromInfluence: 5 },
      { id: 'cmp-3', name: 'Слушатели / Listener', health: 'warning', healthPercent: 62, damageFromComponents: 12, damageFromInfluence: 8 },
    ],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 2, major: 1, minor: 0, warning: 0, normal: 0 },
    timeline: timeline24h(35, [92, 88, 80, 70, 55, 45, 38, 35, 40, 42, 35, 35]),
  },
  'ci-002': {
    ciId: 'ci-002',
    currentHealth: 'major',
    healthPercent: 58,
    minToday: 'major',
    minTodayPercent: 52,
    maxToday: 'ok',
    maxTodayPercent: 88,
    components: [
      { id: 'cmp-1', name: 'Nginx / HTTP', health: 'major', healthPercent: 55, damageFromComponents: 35, damageFromInfluence: 20 },
      { id: 'cmp-2', name: 'CPU / Load', health: 'warning', healthPercent: 68, damageFromComponents: 20, damageFromInfluence: 10 },
    ],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 0, major: 2, minor: 1, warning: 1, normal: 0 },
    timeline: timeline24h(58, [88, 85, 82, 75, 68, 62, 58, 58, 60, 58, 55, 58]),
  },
  'ci-003': {
    ciId: 'ci-003',
    currentHealth: 'warning',
    healthPercent: 72,
    minToday: 'warning',
    minTodayPercent: 65,
    maxToday: 'ok',
    maxTodayPercent: 95,
    components: [
      { id: 'cmp-1', name: 'Kubelet', health: 'warning', healthPercent: 70, damageFromComponents: 18, damageFromInfluence: 5 },
      { id: 'cmp-2', name: 'Pods / analytics', health: 'major', healthPercent: 50, damageFromComponents: 28, damageFromInfluence: 0 },
    ],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 0, major: 1, minor: 0, warning: 1, normal: 0 },
    timeline: timeline24h(72, [95, 90, 88, 85, 80, 78, 72, 72, 74, 72, 70, 72]),
  },
  'ci-005': {
    ciId: 'ci-005',
    currentHealth: 'warning',
    healthPercent: 82,
    minToday: 'warning',
    minTodayPercent: 78,
    maxToday: 'ok',
    maxTodayPercent: 98,
    components: [
      { id: 'cmp-1', name: 'Proxy service', health: 'warning', healthPercent: 80, damageFromComponents: 10, damageFromInfluence: 0 },
    ],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 0, major: 0, minor: 1, warning: 0, normal: 0 },
    timeline: timeline24h(82, [98, 95, 92, 90, 88, 85, 82, 82, 84, 82, 80, 82]),
  },
  'ci-006': {
    ciId: 'ci-006',
    currentHealth: 'ok',
    healthPercent: 96,
    minToday: 'ok',
    minTodayPercent: 94,
    maxToday: 'ok',
    maxTodayPercent: 100,
    components: [
      { id: 'cmp-1', name: 'Broker', health: 'ok', healthPercent: 96, damageFromComponents: 2, damageFromInfluence: 0 },
    ],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 0, major: 0, minor: 1, warning: 0, normal: 0 },
    timeline: timeline24h(96, [100, 98, 98, 97, 96, 96, 95, 96, 96, 97, 96, 96]),
  },
};

export function getCiHealth(ciId: string): CiHealthProfile {
  return ciHealthProfiles[ciId] ?? {
    ciId,
    currentHealth: 'ok',
    healthPercent: 100,
    minToday: 'ok',
    minTodayPercent: 100,
    maxToday: 'ok',
    maxTodayPercent: 100,
    components: [{ id: 'cmp-default', name: 'Основной компонент', health: 'ok', healthPercent: 100, damageFromComponents: 0, damageFromInfluence: 0 }],
    dependents: [],
    signalsBySeverity: { fatal: 0, critical: 0, major: 0, minor: 0, warning: 0, normal: 0 },
    timeline: timeline24h(100, Array(12).fill(100)),
  };
}

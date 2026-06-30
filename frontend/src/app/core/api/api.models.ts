/** Types derived from docs/fm-module/api.yaml and docs/api-gateway.yaml */

export type Severity = 'fatal' | 'critical' | 'major' | 'minor' | 'warning' | 'normal';
export type EventStatus = 'new' | 'in_progress' | 'closed' | 'archived' | 'maintenance' | 'deferred';
export type SourceType = 'push_rest' | 'push_snmp_trap' | 'pull_etl';
export type SourceStatus = 'active' | 'inactive' | 'blocked';
export type AdapterServiceStatus = 'ok' | 'degraded' | 'down';
export type AdapterRuntimeStatus = 'running' | 'stopped' | 'idle' | 'degraded' | 'unreachable';
export type RuleType = 'dedup' | 'problem_resolution' | 'correlation' | 'threshold';
export type ApprovalStatus = 'approved' | 'pending' | 'draft';
export type HealthLevel = 'fatal' | 'critical' | 'major' | 'warning' | 'ok' | 'unknown';
export type EventActionType = 'take' | 'close' | 'comment' | 'defer' | 'maintenance';

export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ErrorResponse {
  error: string;
  message: string;
  details?: { field?: string; message?: string }[];
}

export interface LoginRequest {
  login: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface User {
  id: string;
  login: string;
  fullName: string;
  email: string;
  roleIds: string[];
  team: string;
  active: boolean;
}

export interface UserCreateRequest {
  login: string;
  fullName: string;
  email: string;
  password: string;
  roleIds: string[];
  team?: string;
  active?: boolean;
}

export interface UserPatchRequest {
  fullName?: string;
  email?: string;
  roleIds?: string[];
  team?: string;
  active?: boolean;
  password?: string;
}

export interface Role {
  id: string;
  name: string;
  description: string;
  permissions: string[];
}

export interface RoleCreateRequest {
  name: string;
  description?: string;
  permissions: string[];
}

export interface RolePatchRequest {
  name?: string;
  description?: string;
  permissions?: string[];
}

export interface Event {
  id: string;
  status: EventStatus;
  severity: Severity;
  title: string;
  description: string;
  assignedUserId?: string;
  assignedUserName?: string;
  repeatCount: number;
  ciId: string;
  nodeFqdn: string;
  systemName: string;
  subsystemName?: string;
  sourceId: string;
  sourceName?: string;
  rootEventId?: string;
  childEventIds?: string[];
  itsmIncidentNumber?: string;
  tags?: string[];
  createdAt: string;
  sourceAt: string;
  lastRepeatAt?: string;
  takenAt?: string;
  closedAt?: string;
  updatedAt?: string;
}

export interface EventAttribute {
  key: string;
  type: 'string' | 'number' | 'boolean' | 'date' | 'uuid';
  value: string | number | boolean;
}

export interface EventActionLog {
  id: string;
  eventId: string;
  action: string;
  userName: string;
  userId?: string;
  timestamp: string;
  details: string;
}

export interface EventDetail extends Event {
  attributes?: EventAttribute[];
  actionLogs?: EventActionLog[];
  rawEventId?: string;
}

export interface EventPage {
  items: Event[];
  page: PageMeta;
}

export interface RawEvent {
  id: string;
  sourceId: string;
  sourceName?: string;
  title: string;
  severity: Severity;
  processed: boolean;
  externalId: string;
  nodeFqdn?: string;
  createdAt: string;
  occurredAt: string;
}

export interface RawEventPage {
  items: RawEvent[];
  page: PageMeta;
}

export interface EventSource {
  id: string;
  name: string;
  type: SourceType;
  status: SourceStatus;
  protocol?: string;
  endpoint?: string;
  apiKeyMasked?: string;
  adapterVersion?: string;
  lastSuccessAt?: string;
  schedule?: string;
}

export interface EventSourceDetail extends EventSource {
  webhookUrl?: string;
  filterRules?: Record<string, unknown>;
  parserConfig?: Record<string, unknown>;
}

export interface SourceTestResult {
  success: boolean;
  message: string;
  testedAt?: string;
  probeEventId?: string;
  delivery?: 'forwarded' | 'buffered' | 'failed';
  latencyMs?: number;
}

export interface BindSimulatorResponse {
  success: boolean;
  message?: string;
  sourceWebhookKey?: string;
  adapterWebhookUrl?: string;
  simulatorEnabled?: boolean;
}

export interface SourceSimulatorStatus {
  reachable: boolean;
  bound: boolean;
  enabled: boolean;
  sourceWebhookKey?: string;
  adapterWebhookUrl?: string;
  simulatorBaseUrl?: string;
  activeProblems?: number;
  message?: string;
}

export interface SourceSimulatorTickResult {
  kind: 'problem' | 'recovery' | 'skip' | 'error';
  scenarioId?: string | null;
  delivered: boolean;
  httpStatus?: number | null;
  error?: string | null;
  note?: string | null;
}

export interface SourceSimulatorControlResult {
  success: boolean;
  enabled: boolean;
  message?: string;
}

export interface AdapterServiceRuntime {
  status: AdapterServiceStatus;
  version: string;
  database: 'up' | 'down';
  fmModule: 'reachable' | 'unreachable';
  bufferedMessages: number;
  baseUrl: string;
}

export interface AdapterSourceRuntime {
  sourceId: string;
  name: string;
  type: SourceType;
  configStatus: SourceStatus;
  adapterRuntimeStatus: AdapterRuntimeStatus;
  adapterVersion?: string;
  lastSuccessAt?: string;
}

export interface AdapterRuntimeResponse {
  service: AdapterServiceRuntime;
  sources: AdapterSourceRuntime[];
}

export interface ProcessingRule {
  id: string;
  name: string;
  ruleType: RuleType;
  enabled: boolean;
  triggerType: string;
  lastRunAt?: string;
  approvalStatus: ApprovalStatus;
  description: string;
}

export interface RuleCanvasNode {
  id: string;
  type: string;
  position?: { x: number; y: number };
  data?: Record<string, unknown>;
}

export interface RuleCanvas {
  nodes: RuleCanvasNode[];
  edges: { id: string; source: string; target: string }[];
}

export interface ProcessingRuleDetail extends ProcessingRule {
  canvas?: RuleCanvas;
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

export interface ProductHealthDetail extends ProductHealth {
  configurationItems?: ConfigurationItem[];
  activeEvents?: Event[];
  severityBreakdown?: Record<string, number>;
}

export interface ProductAdmin {
  id: string;
  name: string;
  code: string;
  tenant: string;
  site: string;
  tags: string[];
  ciIds: string[];
}

export interface ProductCreateRequest {
  name: string;
  code: string;
  tenant: string;
  site: string;
  tags?: string[];
  ciIds?: string[];
}

export interface ProductPatchRequest {
  name?: string;
  code?: string;
  tenant?: string;
  site?: string;
  tags?: string[];
  ciIds?: string[];
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

export interface ConfigurationItemCreateRequest {
  fqdn: string;
  ciType: string;
  system: string;
  subsystem?: string;
  software?: string;
  productIds?: string[];
  tags?: string[];
}

export interface ConfigurationItemPatchRequest {
  fqdn?: string;
  ciType?: string;
  system?: string;
  subsystem?: string;
  software?: string;
  productIds?: string[];
  tags?: string[];
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

export interface DashboardSummary {
  severityCounts: {
    fatal: number;
    critical: number;
    major: number;
    minor: number;
    warning: number;
    normal: number;
  };
  totalActive: number;
  productPreview: ProductHealth[];
  systemMaps: EventMap[];
}

export interface Settings {
  timezone: string;
  pollingIntervalSec: number;
  autoArchiveDays: number;
  repeatIntervalMin: number;
  wislaIntegration: boolean;
  itsmIntegration: boolean;
}

export interface UserPreferences {
  sidebarCollapsed?: boolean;
  defaultMapId?: string;
  columnLayouts?: Record<string, unknown>;
}

export interface SettingsBundle {
  module: Settings;
  profile: UserPreferences;
}

export interface NotificationSettings {
  emailEnabled?: boolean;
  telegramEnabled?: boolean;
  rules?: Record<string, unknown>[];
}

export interface PushNotification {
  id: string;
  ruleId: string;
  eventId: string;
  title: string;
  message: string;
  createdAt: string;
}

export interface PushNotificationList {
  items: PushNotification[];
}

export interface IntegrationSettings {
  wisla?: { enabled?: boolean; baseUrl?: string; apiToken?: string };
  itsm?: { enabled?: boolean; endpoint?: string; mapping?: Record<string, unknown> };
}

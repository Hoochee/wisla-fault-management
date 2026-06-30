import { Injectable, inject } from '@angular/core';

import { Observable, map } from 'rxjs';

import { ApiClientService } from './api-client.service';

import {

  AdapterRuntimeResponse,

  ConfigurationItem,

  ConfigurationItemCreateRequest,

  ConfigurationItemPatchRequest,

  DashboardSummary,

  Event,

  EventActionLog,

  EventActionType,

  EventDetail,

  EventMap,

  EventPage,

  EventSource,

  EventSourceDetail,

  IntegrationSettings,

  NotificationSettings,

  ProcessingRule,

  ProcessingRuleDetail,

  ProductAdmin,

  ProductCreateRequest,

  ProductHealth,

  ProductHealthDetail,

  ProductPatchRequest,

  PushNotificationList,

  RawEventPage,

  Role,

  RoleCreateRequest,

  RolePatchRequest,

  SettingsBundle,

  BindSimulatorResponse,

  SourceSimulatorControlResult,

  SourceSimulatorStatus,

  SourceSimulatorTickResult,

  SourceTestResult,

  User,

  UserCreateRequest,

  UserPatchRequest,

} from './api.models';



interface EventDetailResponse {

  event: Event;

  actionLogs: EventActionLog[];

  rawEventId?: string;

}



interface BackendEventSource {

  id: string;

  name: string;

  type: string;

  protocol?: string;

  endpoint?: string;

  apiKey?: string;

  adapterVersion?: string;

  lastSuccessAt?: string;

  status: EventSource['status'];

  schedule?: string;

}



interface EventSourceDetailResponse {

  source: BackendEventSource;

  filterRules?: Record<string, unknown>;

  parserConfig?: Record<string, unknown>;

  webhookUrl?: string;

}



export interface SourceCreateRequest {

  name: string;

  type: string;

  protocol: string;

  endpoint: string;

  schedule?: string;

  status?: string;

}



export interface SourceMutationResult extends EventSource {

  apiKey?: string;

}



export interface SourcePatchRequest {

  name?: string;

  protocol?: string;

  endpoint?: string;

  status?: string;

  regenerateApiKey?: boolean;

}



export interface EventActionResult {

  event: Event;

  logEntry: EventActionLog;

}



export interface RuleCreateRequest {

  name: string;

  ruleType: string;

  triggerType: string;

  description?: string;

  canvas?: ProcessingRuleDetail['canvas'];

}



export interface RulePatchRequest {

  name?: string;

  description?: string;

  triggerType?: string;

  canvas?: ProcessingRuleDetail['canvas'];

  enabled?: boolean;

}



function maskApiKey(key?: string): string | undefined {

  if (!key) return undefined;

  if (key.length <= 8) return '****';

  return `${key.slice(0, 4)}…${key.slice(-4)}`;

}



function mapSource(s: BackendEventSource): EventSource {

  return {

    id: s.id,

    name: s.name,

    type: s.type as EventSource['type'],

    status: s.status,

    endpoint: s.endpoint,

    protocol: s.protocol,

    apiKeyMasked: maskApiKey(s.apiKey),

    adapterVersion: s.adapterVersion,

    lastSuccessAt: s.lastSuccessAt,

    schedule: s.schedule,

  };

}



@Injectable({ providedIn: 'root' })

export class FmApiService {

  private readonly api = inject(ApiClientService);



  getDashboardSummary(): Observable<DashboardSummary> {

    return this.api.get<DashboardSummary>('/dashboard/summary');

  }



  getProducts(): Observable<ProductHealth[]> {

    return this.api.get<ProductHealth[]>('/health/products');

  }



  getProduct(id: string): Observable<ProductHealthDetail> {

    return this.api.get<ProductHealthDetail>(`/health/products/${id}`);

  }



  listProductsAdmin(): Observable<ProductAdmin[]> {

    return this.api.get<ProductAdmin[]>('/admin/products');

  }



  getProductAdmin(id: string): Observable<ProductAdmin> {

    return this.api.get<ProductAdmin>(`/admin/products/${id}`);

  }



  createProduct(request: ProductCreateRequest): Observable<ProductAdmin> {

    return this.api.post<ProductAdmin>('/admin/products', request);

  }



  patchProduct(id: string, patch: ProductPatchRequest): Observable<ProductAdmin> {

    return this.api.patch<ProductAdmin>(`/admin/products/${id}`, patch);

  }



  deleteProduct(id: string): Observable<void> {

    return this.api.delete<void>(`/admin/products/${id}`);

  }



  listEvents(params?: Record<string, string | number | boolean | string[]>): Observable<EventPage> {

    return this.api.get<EventPage>('/events', params);

  }



  getEvent(id: string): Observable<EventDetail> {

    return this.api.get<EventDetailResponse>(`/events/${id}`).pipe(

      map((r) => ({

        ...r.event,

        actionLogs: r.actionLogs,

        rawEventId: r.rawEventId,

      })),

    );

  }



  performEventAction(eventId: string, action: EventActionType, comment?: string): Observable<EventActionResult> {

    return this.api.post<EventActionResult>(`/events/${eventId}/actions`, { action, comment });

  }



  listRawEvents(params?: Record<string, string | number | boolean | string[]>): Observable<RawEventPage> {

    return this.api.get<RawEventPage>('/raw-events', params);

  }



  listEventMaps(): Observable<EventMap[]> {

    return this.getDashboardSummary().pipe(map((s) => s.systemMaps));

  }



  listSources(): Observable<EventSource[]> {

    return this.api.get<BackendEventSource[]>('/sources').pipe(map((items) => items.map(mapSource)));

  }



  getAdapterRuntime(): Observable<AdapterRuntimeResponse> {

    return this.api.get<AdapterRuntimeResponse>('/adapters/runtime');

  }



  getSource(id: string): Observable<EventSourceDetail> {

    return this.api.get<EventSourceDetailResponse>(`/sources/${id}`).pipe(

      map((r) => ({

        ...mapSource(r.source),

        webhookUrl: r.webhookUrl,

        filterRules: r.filterRules,

        parserConfig: r.parserConfig,

      })),

    );

  }



  createSource(request: SourceCreateRequest): Observable<SourceMutationResult> {

    return this.api.post<BackendEventSource & { apiKey?: string }>('/sources', request).pipe(

      map((s) => ({ ...mapSource(s), apiKey: s.apiKey })),

    );

  }



  patchSource(id: string, patch: SourcePatchRequest): Observable<SourceMutationResult> {

    return this.api.patch<BackendEventSource & { apiKey?: string }>(`/sources/${id}`, patch).pipe(

      map((s) => ({ ...mapSource(s), apiKey: s.apiKey })),

    );

  }



  deleteSource(id: string): Observable<void> {

    return this.api.delete<void>(`/sources/${id}`);

  }



  testSource(id: string, ingestApiKey?: string): Observable<SourceTestResult> {

    const body = ingestApiKey ? { ingestApiKey } : {};

    return this.api.post<SourceTestResult>(`/sources/${id}/test`, body);

  }



  bindSimulator(id: string, ingestApiKey: string): Observable<BindSimulatorResponse> {

    return this.api.post<BindSimulatorResponse>(`/sources/${id}/bind-simulator`, { ingestApiKey });

  }



  getSimulatorStatus(id: string): Observable<SourceSimulatorStatus> {

    return this.api.get<SourceSimulatorStatus>(`/sources/${id}/simulator-status`);

  }



  sendTestEvent(id: string): Observable<SourceSimulatorTickResult> {

    return this.api.post<SourceSimulatorTickResult>(`/sources/${id}/send-test-event`);

  }



  setSimulatorControl(id: string, enabled: boolean): Observable<SourceSimulatorControlResult> {

    return this.api.post<SourceSimulatorControlResult>(`/sources/${id}/simulator-control`, { enabled });

  }



  listRules(): Observable<ProcessingRule[]> {

    return this.api.get<ProcessingRule[]>('/rules');

  }



  getRule(id: string): Observable<ProcessingRuleDetail> {

    return this.api.get<ProcessingRuleDetail>(`/rules/${id}`);

  }



  createRule(request: RuleCreateRequest): Observable<ProcessingRuleDetail> {

    return this.api.post<ProcessingRuleDetail>('/rules', request);

  }



  patchRule(id: string, patch: RulePatchRequest): Observable<ProcessingRuleDetail> {

    return this.api.patch<ProcessingRuleDetail>(`/rules/${id}`, patch);

  }



  listUsers(): Observable<User[]> {

    return this.api.get<{ items: User[] }>('/admin/users').pipe(map((res) => res.items));

  }



  createUser(request: UserCreateRequest): Observable<User> {

    return this.api.post<User>('/admin/users', request);

  }



  patchUser(id: string, patch: UserPatchRequest): Observable<User> {

    return this.api.patch<User>(`/admin/users/${id}`, patch);

  }



  deleteUser(id: string): Observable<void> {

    return this.api.delete<void>(`/admin/users/${id}`);

  }



  listRoles(): Observable<Role[]> {

    return this.api.get<Role[]>('/admin/roles');

  }



  createRole(request: RoleCreateRequest): Observable<Role> {

    return this.api.post<Role>('/admin/roles', request);

  }



  patchRole(id: string, patch: RolePatchRequest): Observable<Role> {

    return this.api.patch<Role>(`/admin/roles/${id}`, patch);

  }



  listConfigurationItems(): Observable<ConfigurationItem[]> {

    return this.api.get<{ items: ConfigurationItem[] }>('/admin/configuration-items').pipe(

      map((res) => res.items),

    );

  }



  createConfigurationItem(request: ConfigurationItemCreateRequest): Observable<ConfigurationItem> {

    return this.api.post<ConfigurationItem>('/admin/configuration-items', request);

  }



  patchConfigurationItem(id: string, patch: ConfigurationItemPatchRequest): Observable<ConfigurationItem> {

    return this.api.patch<ConfigurationItem>(`/admin/configuration-items/${id}`, patch);

  }



  deleteConfigurationItem(id: string): Observable<void> {

    return this.api.delete<void>(`/admin/configuration-items/${id}`);

  }



  getSettings(): Observable<SettingsBundle> {

    return this.api.get<SettingsBundle>('/settings');

  }



  patchSettings(patch: Partial<SettingsBundle>): Observable<SettingsBundle> {

    return this.api.patch<SettingsBundle>('/settings', patch);

  }



  getNotificationSettings(): Observable<NotificationSettings> {

    return this.api.get<NotificationSettings>('/settings/notifications');

  }



  getIntegrationSettings(): Observable<IntegrationSettings> {

    return this.api.get<IntegrationSettings>('/settings/integrations');

  }



  getPushNotifications(since?: string): Observable<PushNotificationList> {

    const params = since ? { since } : undefined;

    return this.api.get<PushNotificationList>('/notifications/push', params);

  }

}



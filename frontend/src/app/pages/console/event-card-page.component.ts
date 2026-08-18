import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { FmApiService } from '../../core/api/fm-api.service';
import { Event, EventDetail } from '../../core/api/api.models';
import { SeverityBadgeComponent } from '../../shared/severity-badge/severity-badge.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { EventDutyActionsComponent } from './event-duty-actions.component';

type EventTab = 'attributes' | 'journal' | 'relations';

@Component({
  selector: 'app-event-card',
  standalone: true,
  imports: [RouterLink, DatePipe, SeverityBadgeComponent, StatusBadgeComponent, EventDutyActionsComponent],
  template: `
    @if (event) {
      <div class="header">
        <a routerLink="/console" class="back">← Консоль</a>
        <div class="title-row">
          <app-status-badge [status]="event.status" />
          <app-severity-badge [severity]="event.severity" />
          @if (event.repeatCount > 1) {
            <span class="repeat">×{{ event.repeatCount }}</span>
          }
          <h1>{{ event.title }}</h1>
        </div>
        <p class="meta mono">ID: {{ event.id }} · {{ event.nodeFqdn }} · {{ event.createdAt | date:'medium' }}</p>
      </div>

      <div class="info-grid">
        <div class="info-block">
          <span class="info-label">КЕ / FQDN</span>
          <span class="info-value mono">{{ event.nodeFqdn || '—' }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">Система</span>
          <span class="info-value">{{ event.systemName || '—' }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">Источник</span>
          <span class="info-value">{{ event.sourceName || '—' }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">Исполнитель</span>
          <span class="info-value">{{ event.assignedUserName || '—' }}</span>
        </div>
      </div>

      <div class="tabs">
        @for (tab of tabs; track tab.id) {
          <button
            type="button"
            class="tab"
            [class.active]="activeTab() === tab.id"
            (click)="selectTab(tab.id)"
          >
            {{ tab.label }}
          </button>
        }
      </div>

      <app-event-duty-actions [event]="event" [disabled]="acting()" (acted)="onDutyActed()" />

      <div class="panel">
        @switch (activeTab()) {
          @case ('attributes') {
            <div class="panel-toolbar">
              <button type="button" class="link-btn" (click)="jsonMode.set(!jsonMode())">
                {{ jsonMode() ? 'Таблица' : 'JSON' }}
              </button>
            </div>
            @if (jsonMode()) {
              <pre class="json-block">{{ attributesJson() }}</pre>
            } @else {
              <table class="attr-table">
                <thead>
                  <tr><th>Ключ</th><th>Тип</th><th>Значение</th></tr>
                </thead>
                <tbody>
                  @for (row of attributeRows(); track row.key) {
                    <tr>
                      <td class="mono muted">{{ row.key }}</td>
                      <td class="muted">{{ row.type }}</td>
                      <td>{{ row.value || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          }
          @case ('journal') {
            @if (!event.actionLogs?.length) {
              <p class="empty">Журнал пуст</p>
            } @else {
              @for (log of event.actionLogs; track log.id) {
                <div class="log-entry">
                  <span class="mono log-time">{{ log.timestamp | date:'short' }}</span>
                  <div>
                    <span class="log-action">{{ formatAction(log.action) }}</span>
                    <span class="log-user"> — {{ log.userName }}</span>
                    <p class="log-details">{{ log.details }}</p>
                  </div>
                </div>
              }
            }
          }
          @case ('relations') {
            @if (rootEvent(); as root) {
              <div class="rel-section">
                <h3>Корневая причина</h3>
                <a [routerLink]="['/console', root.id]" class="rel-link">
                  <app-severity-badge [severity]="root.severity" />
                  <span>{{ root.title }}</span>
                </a>
              </div>
            }
            @if (childEvents().length) {
              <div class="rel-section">
                <h3>Дочерние события</h3>
                @for (child of childEvents(); track child.id) {
                  <a [routerLink]="['/console', child.id]" class="rel-link">
                    <app-severity-badge [severity]="child.severity" />
                    <span>{{ child.title }}</span>
                  </a>
                }
              </div>
            }
            @if (event.ciId || event.nodeFqdn) {
              <div class="rel-section">
                <h3>КЕ</h3>
                <div class="ci-card">
                  <span class="mono">{{ event.nodeFqdn || event.ciId }}</span>
                  @if (event.systemName) {
                    <span class="muted">{{ event.systemName }}</span>
                  }
                </div>
              </div>
            }
            @if (event.rawEventId) {
              <div class="rel-section">
                <h3>Первичное событие</h3>
                <a routerLink="/events/raw" class="rel-link mono">{{ event.rawEventId }}</a>
              </div>
            }
            @if (!rootEvent() && !childEvents().length && !event.ciId && !event.nodeFqdn && !event.rawEventId) {
              <p class="empty">Нет связанных событий</p>
            }
          }
        }
      </div>
    }
  `,
  styles: [
    `
      .back { color: var(--accent); text-decoration: none; font-size: 0.8125rem; }
      .title-row { display: flex; align-items: center; gap: 0.75rem; margin: 0.75rem 0 0.25rem; flex-wrap: wrap; }
      h1 { margin: 0; font-size: 1.25rem; color: var(--text-primary); }
      .repeat { color: var(--text-muted); font-size: 0.875rem; }
      .meta { color: var(--text-muted); font-size: 0.8125rem; }
      .info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 0.75rem; margin: 1rem 0; }
      .info-block { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 6px; padding: 0.625rem 0.75rem; }
      .info-label { display: block; font-size: 0.625rem; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.25rem; }
      .info-value { font-size: 0.8125rem; color: var(--text-primary); }
      .tabs { display: flex; gap: 0.25rem; margin: 1rem 0 0.75rem; border-bottom: 1px solid var(--border); }
      .tab { padding: 0.5rem 1rem; background: none; border: none; color: var(--text-muted); cursor: pointer; border-bottom: 2px solid transparent; font-size: 0.8125rem; }
      .tab:hover { color: var(--text-primary); }
      .tab.active { color: var(--accent); border-bottom-color: var(--accent); }
      .panel { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .panel-toolbar { display: flex; justify-content: flex-end; margin-bottom: 0.75rem; }
      .link-btn { background: none; border: none; color: var(--accent); cursor: pointer; font-size: 0.75rem; }
      .json-block { margin: 0; padding: 1rem; background: var(--bg-primary); border-radius: 6px; font-size: 0.75rem; overflow: auto; color: var(--text-secondary); }
      .attr-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }
      .attr-table th, .attr-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }
      .attr-table th { color: var(--text-muted); font-size: 0.75rem; }
      .muted { color: var(--text-muted); }
      .mono { font-family: var(--font-mono); }
      .empty { margin: 0; color: var(--text-muted); font-size: 0.875rem; }
      .log-entry { display: flex; gap: 0.75rem; padding: 0.5rem 0; border-bottom: 1px solid var(--border); font-size: 0.8125rem; border-left: 2px solid rgba(47, 111, 237, 0.3); padding-left: 0.75rem; }
      .log-time { color: var(--text-muted); white-space: nowrap; font-size: 0.75rem; }
      .log-action { color: var(--text-primary); font-weight: 500; }
      .log-user { color: var(--text-muted); }
      .log-details { margin: 0.25rem 0 0; color: var(--text-secondary); font-size: 0.75rem; }
      .rel-section { margin-bottom: 1.25rem; }
      .rel-section h3 { margin: 0 0 0.5rem; font-size: 0.6875rem; text-transform: uppercase; color: var(--text-muted); font-weight: 500; }
      .rel-link { display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; margin-bottom: 0.375rem; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 6px; text-decoration: none; color: var(--text-secondary); font-size: 0.8125rem; }
      .rel-link:hover { border-color: var(--accent); }
      .ci-card { padding: 0.5rem 0.75rem; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 6px; font-size: 0.8125rem; display: flex; gap: 0.5rem; flex-wrap: wrap; }
    `,
  ],
})
export class EventCardPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  private readonly route = inject(ActivatedRoute);

  event: EventDetail | null = null;
  readonly acting = signal(false);
  readonly activeTab = signal<EventTab>('attributes');
  readonly jsonMode = signal(false);
  readonly rootEvent = signal<Event | null>(null);
  readonly childEvents = signal<Event[]>([]);

  readonly tabs: { id: EventTab; label: string }[] = [
    { id: 'attributes', label: 'Атрибуты' },
    { id: 'journal', label: 'Журнал действий' },
    { id: 'relations', label: 'Связи' },
  ];

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('eventId')!;
    this.loadEvent(id);
  }

  selectTab(tab: EventTab): void {
    this.activeTab.set(tab);
  }

  onDutyActed(): void {
    if (!this.event) return;
    this.loadEvent(this.event.id);
    this.activeTab.set('journal');
  }

  formatAction(action: string): string {
    switch (action) {
      case 'take':
        return 'Принято в работу';
      case 'close':
        return 'Закрыто';
      case 'comment':
        return 'Комментарий';
      case 'ack':
        return 'Подтверждено';
      case 'assign':
        return 'Назначено';
      case 'silence':
        return 'Скрыто';
      default:
        return action;
    }
  }

  attributeRows(): { key: string; type: string; value: string }[] {
    if (!this.event) return [];
    const e = this.event;
    return [
      { key: 'id', type: 'UUID', value: e.id },
      { key: 'status', type: 'string', value: e.status },
      { key: 'severity', type: 'string', value: e.severity },
      { key: 'title', type: 'string', value: e.title },
      { key: 'description', type: 'string', value: e.description ?? '' },
      { key: 'ci_id', type: 'UUID', value: e.ciId ?? '' },
      { key: 'node_fqdn', type: 'string', value: e.nodeFqdn ?? '' },
      { key: 'system_name', type: 'string', value: e.systemName ?? '' },
      { key: 'source_id', type: 'UUID', value: e.sourceId },
      { key: 'repeat_count', type: 'number', value: String(e.repeatCount) },
      { key: 'created_at', type: 'date', value: e.createdAt },
      { key: 'assigned_user_id', type: 'UUID', value: e.assignedUserId ?? '' },
      { key: 'itsm_incident_number', type: 'string', value: e.itsmIncidentNumber ?? '' },
    ];
  }

  attributesJson(): string {
    const rows = this.attributeRows();
    const obj = Object.fromEntries(rows.map((r) => [r.key, r.value]));
    return JSON.stringify(obj, null, 2);
  }

  private loadEvent(id: string): void {
    this.api.getEvent(id).subscribe({
      next: (e) => {
        this.event = e;
        this.acting.set(false);
        this.loadRelations(e);
      },
      error: () => this.acting.set(false),
    });
  }

  private loadRelations(event: EventDetail): void {
    const rootId = event.rootEventId;
    const childIds = event.childEventIds ?? [];
    const root$ = rootId
      ? this.api.getEvent(rootId).pipe(
          map((d) => d as Event),
          catchError(() => of(null)),
        )
      : of(null);
    const children$ =
      childIds.length > 0
        ? forkJoin(
            childIds.map((cid) =>
              this.api.getEvent(cid).pipe(
                map((d) => d as Event),
                catchError(() => of(null)),
              ),
            ),
          ).pipe(map((items) => items.filter((x): x is Event => x !== null)))
        : of([] as Event[]);

    forkJoin({ root: root$, children: children$ }).subscribe(({ root, children }) => {
      this.rootEvent.set(root);
      this.childEvents.set(children);
    });
  }
}

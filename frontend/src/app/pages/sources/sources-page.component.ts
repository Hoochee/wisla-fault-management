import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, forkJoin, interval, of, startWith, Subscription, switchMap } from 'rxjs';
import { FmApiService } from '../../core/api/fm-api.service';
import {
  AdapterRuntimeResponse,
  AdapterRuntimeStatus,
  AdapterServiceRuntime,
  AdapterServiceStatus,
  EventSource,
  SourceStatus,
  SourceType,
} from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

type SourceFilter = 'all' | 'running' | 'stopped' | 'push';

const SOURCE_TYPE_LABELS: Record<SourceType, string> = {
  push_rest: 'Push REST',
  push_snmp_trap: 'SNMP Trap',
  pull_etl: 'Pull ETL',
};

const SOURCE_STATUS_LABELS: Record<SourceStatus, string> = {
  active: 'Запущен',
  inactive: 'Остановлен',
  blocked: 'Заблокирован',
};

const ADAPTER_RUNTIME_LABELS: Record<AdapterRuntimeStatus, string> = {
  running: 'Запущен',
  stopped: 'Остановлен',
  idle: 'Простой',
  degraded: 'Сбой',
  unreachable: 'Недоступен',
};

const SERVICE_STATUS_LABELS: Record<AdapterServiceRuntime['status'], string> = {
  ok: 'Работает',
  degraded: 'Деградация',
  down: 'Недоступен',
};

const FILTER_LABELS: Record<SourceFilter, string> = {
  all: 'Все',
  running: 'Запущен',
  stopped: 'Остановлен',
  push: 'Push',
};

const POLL_SEC = 30;

interface SourceRow extends EventSource {
  adapterRuntimeStatus?: AdapterRuntimeStatus;
}

@Component({
  selector: 'app-sources',
  standalone: true,
  imports: [RouterLink, DatePipe, PageHeaderComponent],
  template: `
    <app-page-header title="Источники" subtitle="Реестр адаптеров Push/Pull" actionLabel="+ Создать" actionLink="/sources/new" />

    <section class="runtime-card" [attr.data-status]="serviceCardStatus">
      <div class="runtime-head">
        <span class="runtime-dot" [attr.data-status]="serviceCardStatus"></span>
        <h2>Сервис адаптера</h2>
        <span class="runtime-status">{{ serviceStatusLabel }}</span>
      </div>
      @if (runtime) {
        <dl class="runtime-meta">
          <div><dt>Версия</dt><dd>{{ runtime.service.version }}</dd></div>
          <div><dt>Буфер</dt><dd>{{ runtime.service.bufferedMessages }} сообщ.</dd></div>
          <div><dt>fm-module</dt><dd>{{ runtime.service.fmModule === 'reachable' ? 'доступен' : 'недоступен' }}</dd></div>
          <div><dt>База данных</dt><dd>{{ runtime.service.database === 'up' ? 'работает' : 'недоступна' }}</dd></div>
        </dl>
      } @else {
        <p class="runtime-hint">Нет данных о состоянии адаптера</p>
      }
    </section>

    <div class="filters">
      @for (f of filterOptions; track f) {
        <button
          type="button"
          class="filter-chip"
          [class.active]="activeFilter === f"
          (click)="setFilter(f)"
        >
          {{ filterLabel(f) }}
        </button>
      }
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th>Название</th>
          <th>Тип</th>
          <th>Статус</th>
          <th>Адаптер</th>
          <th>API-ключ</th>
          <th>Версия</th>
          <th>Последний успех</th>
          <th>Действия</th>
        </tr>
      </thead>
      <tbody>
        @for (s of filteredSources; track s.id) {
          <tr>
            <td><a [routerLink]="['/sources', s.id]">{{ s.name }}</a></td>
            <td>{{ typeLabel(s.type) }}</td>
            <td>
              <button
                type="button"
                class="status-toggle"
                [attr.data-status]="s.status"
                [disabled]="togglingId() === s.id"
                (click)="toggleStatus(s)"
              >
                {{ statusLabel(s.status) }}
              </button>
            </td>
            <td>
              @if (s.adapterRuntimeStatus) {
                <span class="adapter-badge" [attr.data-status]="s.adapterRuntimeStatus">
                  {{ adapterLabel(s.adapterRuntimeStatus) }}
                </span>
              } @else {
                <span class="muted">—</span>
              }
            </td>
            <td class="mono">{{ s.apiKeyMasked ?? '—' }}</td>
            <td>{{ s.adapterVersion ?? '—' }}</td>
            <td class="mono">{{ s.lastSuccessAt ? (s.lastSuccessAt | date:'short') : '—' }}</td>
            <td class="actions-cell">
              <a [routerLink]="['/sources', s.id]">Изменить</a>
              <button type="button" class="link-btn danger" (click)="confirmDelete(s)">Удалить</button>
            </td>
          </tr>
        }
      </tbody>
    </table>

    @if (actionMessage()) {
      <p class="action-msg">{{ actionMessage() }}</p>
    }

    @if (deleteTarget()) {
      <div class="overlay">
        <div class="modal" (click)="$event.stopPropagation()">
          <h2>Удалить источник?</h2>
          <p>Источник «{{ deleteTarget()!.name }}» будет удалён безвозвратно.</p>
          <div class="modal-actions">
            <button type="button" class="btn" (click)="deleteTarget.set(null)">Отмена</button>
            <button type="button" class="btn-danger" (click)="deleteSource()">Удалить</button>
          </div>
        </div>
      </div>
    }

    @if (deactivateTarget()) {
      <div class="overlay">
        <div class="modal" (click)="$event.stopPropagation()">
          <h2>Невозможно удалить</h2>
          <p>У источника есть связанные события. Деактивировать источник?</p>
          <div class="modal-actions">
            <button type="button" class="btn" (click)="deactivateTarget.set(null)">Отмена</button>
            <button type="button" class="btn-primary" (click)="deactivateSource()">Деактивировать</button>
          </div>
        </div>
      </div>
    }
    <p class="poll-hint">Обновление каждые {{ pollSec }} с</p>
  `,
  styles: [
    `
      .runtime-card {
        margin-bottom: 1rem;
        padding: 1rem;
        border-radius: 8px;
        border: 1px solid var(--border);
        background: var(--bg-sidebar);
      }
      .runtime-card[data-status='ok'] { border-color: rgba(76, 175, 80, 0.4); }
      .runtime-card[data-status='degraded'] { border-color: rgba(255, 193, 7, 0.4); }
      .runtime-card[data-status='down'] { border-color: rgba(244, 67, 54, 0.4); }
      .runtime-head { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; }
      .runtime-head h2 { margin: 0; font-size: 0.9375rem; color: var(--text-primary); flex: 1; }
      .runtime-dot { width: 10px; height: 10px; border-radius: 50%; }
      .runtime-dot[data-status='ok'] { background: #4caf50; }
      .runtime-dot[data-status='degraded'] { background: #ffc107; }
      .runtime-dot[data-status='down'] { background: #f44336; }
      .runtime-status { font-size: 0.8125rem; color: var(--text-muted); }
      .runtime-meta {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
        gap: 0.5rem 1rem;
        margin: 0;
      }
      .runtime-meta div { display: flex; flex-direction: column; gap: 0.125rem; }
      .runtime-meta dt { margin: 0; font-size: 0.6875rem; color: var(--text-muted); text-transform: uppercase; }
      .runtime-meta dd { margin: 0; font-size: 0.8125rem; color: var(--text-secondary); }
      .runtime-hint { margin: 0; font-size: 0.8125rem; color: var(--text-muted); }
      .filters { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 1rem; }
      .filter-chip {
        padding: 0.25rem 0.75rem;
        border-radius: 4px;
        border: 1px solid var(--border);
        background: transparent;
        color: var(--text-muted);
        font-size: 0.75rem;
        cursor: pointer;
      }
      .filter-chip:hover { background: rgba(58, 63, 75, 0.3); color: var(--text-secondary); }
      .filter-chip.active {
        border-color: var(--accent);
        color: var(--accent);
        background: rgba(47, 111, 237, 0.1);
      }
      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }
      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }
      .data-table th { color: var(--text-muted); }
      .mono { font-family: var(--font-mono); font-size: 0.75rem; }
      .muted { color: var(--text-muted); }
      a { color: var(--accent); text-decoration: none; }
      .config-badge, .adapter-badge {
        display: inline-block;
        padding: 2px 8px;
        border-radius: 4px;
        font-size: 11px;
        font-weight: 500;
      }
      .config-badge[data-status='active'] { background: rgba(76, 175, 80, 0.15); color: #4caf50; }
      .config-badge[data-status='inactive'] { background: rgba(158, 158, 158, 0.15); color: var(--text-muted); }
      .config-badge[data-status='blocked'] { background: rgba(244, 67, 54, 0.15); color: #f44336; }
      .adapter-badge[data-status='running'] { background: rgba(76, 175, 80, 0.15); color: #4caf50; }
      .adapter-badge[data-status='stopped'] { background: rgba(158, 158, 158, 0.15); color: var(--text-muted); }
      .adapter-badge[data-status='idle'] { background: rgba(255, 193, 7, 0.15); color: #ffc107; }
      .adapter-badge[data-status='degraded'] { background: rgba(255, 152, 0, 0.15); color: #ff9800; }
      .adapter-badge[data-status='unreachable'] { background: rgba(244, 67, 54, 0.15); color: #f44336; }
      .poll-hint { margin: 0.75rem 0 0; font-size: 0.75rem; color: var(--text-muted); }
      .status-toggle {
        padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; border: none; cursor: pointer;
      }
      .status-toggle[data-status='active'] { background: rgba(76, 175, 80, 0.15); color: #4caf50; }
      .status-toggle[data-status='inactive'] { background: rgba(158, 158, 158, 0.15); color: var(--text-muted); }
      .status-toggle[data-status='blocked'] { background: rgba(244, 67, 54, 0.15); color: #f44336; }
      .status-toggle:disabled { opacity: 0.5; cursor: not-allowed; }
      .actions-cell { display: flex; gap: 0.75rem; align-items: center; }
      .link-btn { padding: 0; border: none; background: none; color: var(--accent); cursor: pointer; font-size: inherit; }
      .link-btn.danger { color: #f44336; }
      .action-msg { margin: 0.5rem 0 0; font-size: 0.8125rem; color: #4caf50; }
      .overlay {
        position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6);
        display: flex; align-items: center; justify-content: center; z-index: 1000;
      }
      .modal {
        background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px;
        padding: 1.25rem; width: min(28rem, 92vw);
      }
      .modal h2 { margin: 0 0 0.75rem; font-size: 1rem; color: var(--text-primary); }
      .modal p { margin: 0 0 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }
      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; }
      .btn, .btn-primary, .btn-danger {
        padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.8125rem; border: 1px solid var(--border);
        background: var(--bg-sidebar); color: var(--text-secondary); cursor: pointer;
      }
      .btn-primary { background: var(--accent); color: #fff; border: none; }
      .btn-danger { background: #c62828; color: #fff; border: none; }
    `,
  ],
})
export class SourcesPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(FmApiService);
  private pollSub?: Subscription;

  readonly pollSec = POLL_SEC;
  readonly filterOptions: SourceFilter[] = ['all', 'running', 'stopped', 'push'];
  readonly togglingId = signal<string | null>(null);
  readonly actionMessage = signal('');
  readonly deleteTarget = signal<SourceRow | null>(null);
  readonly deactivateTarget = signal<SourceRow | null>(null);

  sources: SourceRow[] = [];
  runtime: AdapterRuntimeResponse | null = null;
  activeFilter: SourceFilter = 'all';

  get serviceCardStatus(): AdapterServiceStatus {
    return this.runtime?.service.status ?? 'down';
  }

  get serviceStatusLabel(): string {
    if (!this.runtime) return SERVICE_STATUS_LABELS.down;
    return SERVICE_STATUS_LABELS[this.runtime.service.status];
  }

  get filteredSources(): SourceRow[] {
    return this.sources.filter((s) => this.matchesFilter(s));
  }

  ngOnInit(): void {
    this.pollSub = interval(POLL_SEC * 1000)
      .pipe(
        startWith(0),
        switchMap(() =>
          forkJoin({
            sources: this.api.listSources().pipe(catchError(() => of([] as EventSource[]))),
            runtime: this.api.getAdapterRuntime().pipe(catchError(() => of(null))),
          }),
        ),
      )
      .subscribe(({ sources, runtime }) => this.applyData(sources, runtime));
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  setFilter(filter: SourceFilter): void {
    this.activeFilter = filter;
  }

  filterLabel(filter: SourceFilter): string {
    return FILTER_LABELS[filter];
  }

  typeLabel(type: SourceType): string {
    return SOURCE_TYPE_LABELS[type] ?? type;
  }

  statusLabel(status: SourceStatus): string {
    return SOURCE_STATUS_LABELS[status] ?? status;
  }

  adapterLabel(status: AdapterRuntimeStatus): string {
    return ADAPTER_RUNTIME_LABELS[status] ?? status;
  }

  toggleStatus(source: SourceRow): void {
    const next: SourceStatus = source.status === 'active' ? 'inactive' : 'active';
    this.togglingId.set(source.id);
    this.api.patchSource(source.id, { status: next }).subscribe({
      next: (updated) => {
        this.sources = this.sources.map((s) => (s.id === source.id ? { ...s, ...updated } : s));
        this.actionMessage.set(next === 'active' ? `«${source.name}» запущен` : `«${source.name}» остановлен`);
        this.togglingId.set(null);
      },
      error: () => this.togglingId.set(null),
    });
  }

  confirmDelete(source: SourceRow): void {
    this.deleteTarget.set(source);
  }

  deleteSource(): void {
    const target = this.deleteTarget();
    if (!target) return;
    this.deleteTarget.set(null);
    this.api.deleteSource(target.id).subscribe({
      next: () => {
        this.sources = this.sources.filter((s) => s.id !== target.id);
        this.actionMessage.set(`«${target.name}» удалён`);
      },
      error: (err: HttpErrorResponse) => {
        if (err.status === 409) {
          this.deactivateTarget.set(target);
        }
      },
    });
  }

  deactivateSource(): void {
    const target = this.deactivateTarget();
    if (!target) return;
    this.deactivateTarget.set(null);
    this.api.patchSource(target.id, { status: 'inactive' }).subscribe({
      next: (updated) => {
        this.sources = this.sources.map((s) => (s.id === target.id ? { ...s, ...updated } : s));
        this.actionMessage.set(`«${target.name}» деактивирован`);
      },
    });
  }

  private applyData(sources: EventSource[], runtime: AdapterRuntimeResponse | null): void {
    this.runtime = runtime;
    const runtimeById = new Map(
      (runtime?.sources ?? []).map((r) => [r.sourceId, r.adapterRuntimeStatus]),
    );
    this.sources = sources.map((s) => ({
      ...s,
      adapterRuntimeStatus: runtimeById.get(s.id),
    }));
  }

  private matchesFilter(source: SourceRow): boolean {
    switch (this.activeFilter) {
      case 'running':
        return source.adapterRuntimeStatus === 'running';
      case 'stopped':
        return source.adapterRuntimeStatus === 'stopped';
      case 'push':
        return source.type === 'push_rest' || source.type === 'push_snmp_trap';
      default:
        return true;
    }
  }
}

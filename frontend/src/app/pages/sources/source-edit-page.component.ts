import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { catchError, interval, of, startWith, Subscription, switchMap } from 'rxjs';
import { FmApiService } from '../../core/api/fm-api.service';
import {
  EventSourceDetail,
  SourceSimulatorStatus,
  SourceSimulatorTickResult,
  SourceStatus,
  SourceTestResult,
} from '../../core/api/api.models';
import { getIngestApiKey, setIngestApiKey } from '../../core/api/source-credentials.storage';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

const STATUS_LABELS: Record<SourceStatus, string> = {
  active: 'Запущен',
  inactive: 'Остановлен',
  blocked: 'Заблокирован',
};

const SIMULATOR_POLL_SEC = 12;

@Component({
  selector: 'app-source-edit',
  standalone: true,
  imports: [RouterLink, DatePipe, ReactiveFormsModule, PageHeaderComponent],
  template: `
    @if (source) {
      <app-page-header [title]="source.name" [subtitle]="headerSubtitle" />
      <div class="header-actions">
        <span class="config-badge" [attr.data-status]="source.status">{{ statusLabel(source.status) }}</span>
        <button type="button" class="btn" (click)="toggleStatus()" [disabled]="busy()">
          {{ source.status === 'active' ? 'Остановить' : 'Запустить' }}
        </button>
        <button type="button" class="btn" (click)="test()" [disabled]="testing()">Тест подключения</button>
        @if (!editing()) {
          <button type="button" class="btn" (click)="startEdit()">Редактировать</button>
        }
        <button type="button" class="btn danger" (click)="confirmDelete()" [disabled]="busy()">Удалить</button>
      </div>

      @if (actionMessage()) {
        <p class="action-msg" [class.error]="actionMsgError()">{{ actionMessage() }}</p>
      }

      @if (testing()) {
        <p class="action-hint">Проверка подключения…</p>
      }

      @if (testResult()) {
        <div
          class="test-result test-result-top"
          [class.success]="testResult()!.success"
          [class.buffered]="testResult()!.delivery === 'buffered'"
        >
          <div>{{ testResult()!.message }}</div>
          @if (testResult()!.delivery) {
            <div class="test-meta">Доставка: {{ deliveryLabel(testResult()!.delivery!) }}</div>
          }
          @if (testResult()!.latencyMs != null) {
            <div class="test-meta">Задержка: {{ testResult()!.latencyMs }} мс</div>
          }
        </div>
      }

      @if (editing()) {
        <section class="card edit-form">
          <h2>Редактирование</h2>
          <form [formGroup]="editForm" (ngSubmit)="saveEdit()">
            <label>
              Название
              <input type="text" formControlName="name" />
            </label>
            <label>
              Протокол
              <input type="text" formControlName="protocol" />
            </label>
            <label>
              Endpoint
              <input type="text" formControlName="endpoint" />
            </label>
            <div class="form-actions">
              <button type="button" class="btn-secondary" (click)="cancelEdit()">Отмена</button>
              <button type="submit" class="btn-primary" [disabled]="editForm.invalid || saving()">Сохранить</button>
            </div>
          </form>
        </section>
      }

      <div class="grid">
        <section class="card">
          <h2>Основная информация</h2>
          <dl>
            <dt>ID</dt><dd class="mono">{{ source.id }}</dd>
            <dt>Тип</dt><dd>{{ source.type }}</dd>
            <dt>Протокол</dt><dd>{{ source.protocol ?? '—' }}</dd>
            <dt>Endpoint</dt><dd class="mono">{{ source.endpoint ?? '—' }}</dd>
            <dt>Версия адаптера</dt><dd>{{ source.adapterVersion ?? '—' }}</dd>
            <dt>Последний успех</dt>
            <dd class="mono">{{ source.lastSuccessAt ? (source.lastSuccessAt | date:'medium') : '—' }}</dd>
          </dl>
        </section>
        <section class="card">
          <h2>Получение данных</h2>
          <dl>
            <dt>Webhook URL</dt>
            <dd class="mono copy-field">
              {{ source.webhookUrl ?? '—' }}
              @if (source.webhookUrl) {
                <button type="button" class="link-btn" (click)="copy(source.webhookUrl!)">Копировать</button>
              }
            </dd>
            <dt>API-ключ</dt><dd class="mono">{{ source.apiKeyMasked ?? '—' }}</dd>
          </dl>
          <button type="button" class="btn" (click)="regenerateKey()" [disabled]="busy()">Сгенерировать новый ключ</button>
          @if (testResult()) {
            <div class="test-result" [class.success]="testResult()!.success" [class.buffered]="testResult()!.delivery === 'buffered'">
              <div>{{ testResult()!.message }}</div>
              @if (testResult()!.delivery) {
                <div class="test-meta">Доставка: {{ deliveryLabel(testResult()!.delivery!) }}</div>
              }
              @if (testResult()!.latencyMs != null) {
                <div class="test-meta">Задержка: {{ testResult()!.latencyMs }} мс</div>
              }
            </div>
          }
        </section>
      </div>

      <section class="card demo-block" [class.degraded]="simulatorStatus() && !simulatorStatus()!.reachable">
        <div class="demo-head">
          <h2>Эмулятор Zabbix</h2>
          <span class="sim-badge" [attr.data-state]="simulatorBadgeState()">{{ simulatorBadgeLabel() }}</span>
        </div>
        @if (simulatorStatus(); as sim) {
          @if (!sim.reachable) {
            <p class="demo-error">{{ sim.message ?? 'Симулятор недоступен' }}</p>
          } @else {
            <dl class="demo-meta">
              <div><dt>Webhook</dt><dd class="mono">{{ sim.adapterWebhookUrl ?? '—' }}</dd></div>
              <div><dt>Активные проблемы</dt><dd>{{ sim.activeProblems ?? 0 }}</dd></div>
              <div><dt>Подсказка</dt><dd>{{ sim.message ?? '—' }}</dd></div>
            </dl>
            <div class="demo-actions">
              <button type="button" class="btn" (click)="bindSimulator()" [disabled]="binding()">
                {{ sim.bound ? 'Перепривязать эмулятор' : 'Привязать эмулятор' }}
              </button>
              <button type="button" class="btn" (click)="sendTestEvent()" [disabled]="sendingTick() || !sim.bound">
                Отправить тестовое событие
              </button>
              <label class="toggle">
                <input
                  type="checkbox"
                  [checked]="sim.enabled"
                  [disabled]="togglingAuto() || !sim.bound"
                  (change)="toggleAutoTick($event)"
                />
                Автотик
              </label>
            </div>
            @if (tickResult()) {
              <div class="tick-result" [class.ok]="tickResult()!.delivered">
                Тип: {{ tickKindLabel(tickResult()!.kind) }};
                доставлено: {{ tickResult()!.delivered ? 'да' : 'нет' }}
                @if (tickResult()!.httpStatus != null) { (HTTP {{ tickResult()!.httpStatus }}) }
                @if (tickResult()!.httpStatus === 401) {
                  <div class="tick-hint">Неверный API-ключ: нажмите «Привязать эмулятор» с актуальным ключом (из модалки при создании).</div>
                }
                <a routerLink="/console" class="console-link">Открыть консоль</a>
              </div>
            }
          }
        } @else {
          <p class="demo-hint">Загрузка статуса симулятора…</p>
        }
        <p class="poll-hint">Статус обновляется каждые {{ simulatorPollSec }} с</p>
      </section>

      @if (credentialsModal()) {
        <div class="overlay">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>Новый API-ключ</h2>
            <p class="warning">Сохраните ключ — повторно он не отображается.</p>
            <label>
              API-ключ
              <div class="copy-row">
                <input type="text" readonly [value]="credentialsModal()!.apiKey" />
                <button type="button" class="btn-copy" (click)="copy(credentialsModal()!.apiKey)">Копировать</button>
              </div>
            </label>
            <div class="modal-actions">
              <button type="button" class="btn-primary" (click)="closeCredentialsModal()">Закрыть</button>
            </div>
          </div>
        </div>
      }

      @if (keyPromptOpen()) {
        <div class="overlay">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>Введите API-ключ источника</h2>
            <p class="hint">Ключ нужен для операции в новой сессии браузера.</p>
            <input type="text" class="key-input" [value]="keyPromptValue" (input)="keyPromptValue = $any($event.target).value" />
            <div class="modal-actions">
              <button type="button" class="btn-secondary" (click)="cancelKeyPrompt()">Отмена</button>
              <button type="button" class="btn-primary" (click)="submitKeyPrompt()">Продолжить</button>
            </div>
          </div>
        </div>
      }

      @if (deleteConfirm()) {
        <div class="overlay">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>Удалить источник?</h2>
            <p>Источник «{{ source.name }}» будет удалён безвозвратно.</p>
            <div class="modal-actions">
              <button type="button" class="btn-secondary" (click)="deleteConfirm.set(false)">Отмена</button>
              <button type="button" class="btn-danger" (click)="deleteSource()">Удалить</button>
            </div>
          </div>
        </div>
      }

      @if (deactivateConfirm()) {
        <div class="overlay">
          <div class="modal" (click)="$event.stopPropagation()">
            <h2>Невозможно удалить</h2>
            <p>У источника есть связанные события. Деактивировать источник?</p>
            <div class="modal-actions">
              <button type="button" class="btn-secondary" (click)="deactivateConfirm.set(false)">Отмена</button>
              <button type="button" class="btn-primary" (click)="deactivateSource()">Деактивировать</button>
            </div>
          </div>
        </div>
      }

      <a routerLink="/sources" class="back">← К списку источников</a>
    }
  `,
  styles: [
    `
      .header-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin-bottom: 1rem; }
      .config-badge {
        display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500;
      }
      .config-badge[data-status='active'] { background: rgba(76, 175, 80, 0.15); color: #4caf50; }
      .config-badge[data-status='inactive'] { background: rgba(158, 158, 158, 0.15); color: var(--text-muted); }
      .btn, .btn-primary, .btn-secondary, .btn-danger, .btn-copy {
        padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.8125rem; border: 1px solid var(--border);
        background: var(--bg-sidebar); color: var(--text-secondary); cursor: pointer;
      }
      .btn-primary, .btn-danger { border: none; }
      .btn-primary { background: var(--accent); color: #fff; }
      .btn-danger { background: #c62828; color: #fff; }
      .btn.danger { color: #f44336; border-color: rgba(244, 67, 54, 0.4); }
      .btn:disabled, .btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
      .action-msg { margin: 0 0 1rem; font-size: 0.8125rem; color: #4caf50; }
      .action-msg.error { color: #f44336; }
      .action-hint { margin: 0 0 1rem; font-size: 0.8125rem; color: var(--text-muted); }
      .test-result-top { margin-bottom: 1rem; }
      .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; margin-bottom: 1rem; }
      @media (max-width: 768px) { .grid { grid-template-columns: 1fr; } }
      .card { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; margin-bottom: 1rem; }
      .card h2 { margin: 0 0 0.75rem; font-size: 0.9375rem; color: var(--text-primary); }
      dl { margin: 0; display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8125rem; }
      dt { color: var(--text-muted); }
      dd { margin: 0; color: var(--text-secondary); }
      .mono { font-family: var(--font-mono); font-size: 0.75rem; word-break: break-all; }
      .copy-field { display: flex; flex-direction: column; gap: 0.25rem; }
      .link-btn { align-self: flex-start; padding: 0; border: none; background: none; color: var(--accent); cursor: pointer; font-size: 0.75rem; }
      .test-result { margin-top: 0.75rem; padding: 0.5rem; border-radius: 4px; font-size: 0.8125rem; background: rgba(244,67,54,0.15); }
      .test-result.success { background: rgba(76,175,80,0.15); color: #4caf50; }
      .test-result.buffered { background: rgba(255, 193, 7, 0.15); color: #ffc107; }
      .test-meta { font-size: 0.75rem; margin-top: 0.25rem; opacity: 0.85; }
      .demo-block.degraded { border-color: rgba(244, 67, 54, 0.4); }
      .demo-head { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.75rem; }
      .demo-head h2 { margin: 0; flex: 1; }
      .sim-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 500; }
      .sim-badge[data-state='bound'] { background: rgba(76, 175, 80, 0.15); color: #4caf50; }
      .sim-badge[data-state='unbound'] { background: rgba(158, 158, 158, 0.15); color: var(--text-muted); }
      .sim-badge[data-state='unreachable'] { background: rgba(244, 67, 54, 0.15); color: #f44336; }
      .demo-meta { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 0.5rem; margin-bottom: 0.75rem; }
      .demo-meta div { display: flex; flex-direction: column; gap: 0.125rem; }
      .demo-meta dt { font-size: 0.6875rem; text-transform: uppercase; color: var(--text-muted); }
      .demo-meta dd { margin: 0; font-size: 0.8125rem; color: var(--text-secondary); }
      .demo-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; }
      .toggle { display: flex; align-items: center; gap: 0.375rem; font-size: 0.8125rem; color: var(--text-secondary); cursor: pointer; }
      .tick-result { margin-top: 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }
      .tick-result.ok { color: #4caf50; }
      .tick-hint { margin-top: 0.5rem; color: #ffc107; font-size: 0.75rem; }
      .console-link { margin-left: 0.5rem; color: var(--accent); }
      .demo-error { color: #f44336; font-size: 0.8125rem; }
      .demo-hint, .poll-hint { font-size: 0.75rem; color: var(--text-muted); margin: 0.5rem 0 0; }
      .edit-form label { display: flex; flex-direction: column; gap: 0.375rem; margin-bottom: 0.75rem; font-size: 0.8125rem; color: var(--text-secondary); }
      .edit-form input { padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-sidebar); color: var(--text-primary); }
      .form-actions { display: flex; gap: 0.5rem; }
      .back { color: var(--accent); text-decoration: none; font-size: 0.8125rem; }
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
      .warning { color: #ffc107 !important; }
      .hint { color: var(--text-muted) !important; }
      .copy-row { display: flex; gap: 0.5rem; }
      .copy-row input, .key-input {
        flex: 1; padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border);
        background: var(--bg-sidebar); color: var(--text-primary); font-family: var(--font-mono); font-size: 0.75rem;
      }
      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 1rem; }
    `,
  ],
})
export class SourceEditPageComponent implements OnInit, OnDestroy {
  private readonly api = inject(FmApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  private sourceId = '';
  private pollSub?: Subscription;
  private keyPromptAction: 'test' | 'bind' | null = null;

  readonly simulatorPollSec = SIMULATOR_POLL_SEC;
  source: EventSourceDetail | null = null;
  readonly testing = signal(false);
  readonly busy = signal(false);
  readonly saving = signal(false);
  readonly binding = signal(false);
  readonly sendingTick = signal(false);
  readonly togglingAuto = signal(false);
  readonly editing = signal(false);
  readonly testResult = signal<SourceTestResult | null>(null);
  readonly simulatorStatus = signal<SourceSimulatorStatus | null>(null);
  readonly tickResult = signal<SourceSimulatorTickResult | null>(null);
  readonly actionMessage = signal('');
  readonly actionMsgError = signal(false);
  readonly credentialsModal = signal<{ apiKey: string } | null>(null);
  readonly deleteConfirm = signal(false);
  readonly deactivateConfirm = signal(false);
  readonly keyPromptOpen = signal(false);
  keyPromptValue = '';

  readonly editForm = this.fb.nonNullable.group({
    name: ['', Validators.required],
    protocol: ['', Validators.required],
    endpoint: ['', Validators.required],
  });

  get headerSubtitle(): string {
    return this.editing() ? 'Редактирование источника' : 'Карточка источника';
  }

  ngOnInit(): void {
    this.sourceId = this.route.snapshot.paramMap.get('id')!;
    this.reloadSource();
    this.pollSub = interval(SIMULATOR_POLL_SEC * 1000)
      .pipe(
        startWith(0),
        switchMap(() => this.api.getSimulatorStatus(this.sourceId).pipe(catchError(() => of(null)))),
      )
      .subscribe((status) => {
        if (status) this.simulatorStatus.set(status);
      });
  }

  ngOnDestroy(): void {
    this.pollSub?.unsubscribe();
  }

  statusLabel(status: SourceStatus): string {
    return STATUS_LABELS[status] ?? status;
  }

  simulatorBadgeState(): 'bound' | 'unbound' | 'unreachable' {
    const sim = this.simulatorStatus();
    if (!sim || !sim.reachable) return 'unreachable';
    return sim.bound ? 'bound' : 'unbound';
  }

  simulatorBadgeLabel(): string {
    const state = this.simulatorBadgeState();
    if (state === 'bound') return 'Привязан';
    if (state === 'unreachable') return 'Недоступен';
    return 'Не привязан';
  }

  deliveryLabel(delivery: string): string {
    const labels: Record<string, string> = {
      forwarded: 'доставлено',
      buffered: 'в буфере',
      failed: 'ошибка',
    };
    return labels[delivery] ?? delivery;
  }

  tickKindLabel(kind: string): string {
    const labels: Record<string, string> = {
      problem: 'проблема',
      recovery: 'восстановление',
      skip: 'пропуск',
      error: 'ошибка',
    };
    return labels[kind] ?? kind;
  }

  toggleStatus(): void {
    if (!this.source) return;
    const next: SourceStatus = this.source.status === 'active' ? 'inactive' : 'active';
    this.busy.set(true);
    this.api.patchSource(this.sourceId, { status: next }).subscribe({
      next: (updated) => {
        this.source = { ...this.source!, ...updated };
        this.actionMessage.set(next === 'active' ? 'Источник запущен' : 'Источник остановлен');
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }

  startEdit(): void {
    if (!this.source) return;
    this.editForm.patchValue({
      name: this.source.name,
      protocol: this.source.protocol ?? '',
      endpoint: this.source.endpoint ?? '',
    });
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
  }

  saveEdit(): void {
    if (this.editForm.invalid) return;
    this.saving.set(true);
    this.api.patchSource(this.sourceId, this.editForm.getRawValue()).subscribe({
      next: (updated) => {
        this.source = { ...this.source!, ...updated };
        this.editing.set(false);
        this.actionMessage.set('Изменения сохранены');
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }

  test(): void {
    this.testResult.set(null);
    this.runWithApiKey('test', (key) => {
      this.testing.set(true);
      this.api.testSource(this.sourceId, key).subscribe({
        next: (r) => {
          this.testResult.set(r);
          this.testing.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.testing.set(false);
          const body = err.error as { message?: string } | null;
          this.testResult.set({
            success: false,
            message: body?.message ?? 'Не удалось выполнить тест подключения',
            delivery: 'failed',
          });
        },
      });
    });
  }

  bindSimulator(): void {
    this.runWithApiKey('bind', (key) => {
      this.binding.set(true);
      this.tickResult.set(null);
      this.api.bindSimulator(this.sourceId, key).subscribe({
        next: () => {
          this.binding.set(false);
          this.actionMsgError.set(false);
          this.actionMessage.set('Эмулятор привязан');
          this.refreshSimulatorStatus();
        },
        error: (err: HttpErrorResponse) => {
          this.binding.set(false);
          this.actionMsgError.set(true);
          const body = err.error as { message?: string } | null;
          this.actionMessage.set(body?.message ?? 'Не удалось привязать эмулятор');
        },
      });
    });
  }

  sendTestEvent(): void {
    this.sendingTick.set(true);
    this.api.sendTestEvent(this.sourceId).subscribe({
      next: (r) => {
        this.tickResult.set(r);
        this.sendingTick.set(false);
        this.refreshSimulatorStatus();
      },
      error: () => this.sendingTick.set(false),
    });
  }

  toggleAutoTick(event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    this.togglingAuto.set(true);
    this.api.setSimulatorControl(this.sourceId, checked).subscribe({
      next: () => {
        this.togglingAuto.set(false);
        this.actionMessage.set(checked ? 'Автотик включён' : 'Автотик выключен');
        this.refreshSimulatorStatus();
      },
      error: () => {
        this.togglingAuto.set(false);
        (event.target as HTMLInputElement).checked = !checked;
        this.actionMessage.set('Не удалось изменить автотик');
      },
    });
  }

  regenerateKey(): void {
    this.busy.set(true);
    this.api.patchSource(this.sourceId, { regenerateApiKey: true }).subscribe({
      next: (updated) => {
        this.source = { ...this.source!, ...updated, apiKeyMasked: updated.apiKeyMasked };
        if (updated.apiKey) {
          setIngestApiKey(this.sourceId, updated.apiKey);
          this.credentialsModal.set({ apiKey: updated.apiKey });
        }
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }

  closeCredentialsModal(): void {
    this.credentialsModal.set(null);
  }

  confirmDelete(): void {
    this.deleteConfirm.set(true);
  }

  deleteSource(): void {
    this.deleteConfirm.set(false);
    this.busy.set(true);
    this.api.deleteSource(this.sourceId).subscribe({
      next: () => void this.router.navigate(['/sources']),
      error: (err: HttpErrorResponse) => {
        this.busy.set(false);
        if (err.status === 409) {
          this.deactivateConfirm.set(true);
        }
      },
    });
  }

  deactivateSource(): void {
    this.deactivateConfirm.set(false);
    this.busy.set(true);
    this.api.patchSource(this.sourceId, { status: 'inactive' }).subscribe({
      next: (updated) => {
        this.source = { ...this.source!, ...updated };
        this.actionMessage.set('Источник деактивирован');
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }

  copy(text: string): void {
    void navigator.clipboard.writeText(text);
    this.actionMessage.set('Скопировано в буфер обмена');
  }

  cancelKeyPrompt(): void {
    this.keyPromptOpen.set(false);
    this.keyPromptAction = null;
    this.keyPromptValue = '';
  }

  submitKeyPrompt(): void {
    const key = this.keyPromptValue.trim();
    if (!key) return;
    setIngestApiKey(this.sourceId, key);
    const action = this.keyPromptAction;
    this.cancelKeyPrompt();
    if (action === 'test') this.test();
    if (action === 'bind') this.bindSimulator();
  }

  private runWithApiKey(action: 'test' | 'bind', fn: (key: string) => void): void {
    const key = getIngestApiKey(this.sourceId);
    if (key) {
      fn(key);
      return;
    }
    this.keyPromptAction = action;
    this.keyPromptValue = '';
    this.keyPromptOpen.set(true);
    this.actionMsgError.set(false);
    this.actionMessage.set(
      action === 'test'
        ? 'Введите API-ключ источника для теста подключения'
        : 'Введите API-ключ для привязки эмулятора',
    );
  }

  private reloadSource(): void {
    this.api.getSource(this.sourceId).subscribe((s) => (this.source = s));
  }

  private refreshSimulatorStatus(): void {
    this.api.getSimulatorStatus(this.sourceId).subscribe({
      next: (s) => this.simulatorStatus.set(s),
      error: () => {},
    });
  }
}

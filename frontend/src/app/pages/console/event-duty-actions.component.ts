import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Event as FmEvent, EventActionType, User } from '../../core/api/api.models';
import { EventActionResult, FmApiService } from '../../core/api/fm-api.service';

@Component({
  selector: 'app-event-duty-actions',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <div class="duty-bar">
      <div class="actions">
        <button type="button" class="action-btn" [disabled]="busy()" (click)="run('take')">Принять в работу</button>
        <button type="button" class="action-btn" [disabled]="busy()" (click)="run('close')">Закрыть</button>
        <button type="button" class="action-btn" [disabled]="busy()" (click)="run('ack')">Подтвердить</button>
        <button type="button" class="action-btn" [disabled]="busy()" (click)="openComment()">Комментарий</button>
        <label class="assign-wrap">
          Назначить
          <select
            class="assign-select"
            [disabled]="busy()"
            [value]="event.assignedUserId ?? ''"
            (change)="onAssign($event)"
          >
            <option value="">—</option>
            @for (u of users(); track u.id) {
              <option [value]="u.id">{{ u.fullName }}</option>
            }
          </select>
        </label>
        <span class="silence-group">
          <span class="silence-label">Скрыть</span>
          <button type="button" class="action-btn" [disabled]="busy()" (click)="silence(15)">15</button>
          <button type="button" class="action-btn" [disabled]="busy()" (click)="silence(30)">30</button>
          <button type="button" class="action-btn" [disabled]="busy()" (click)="silence(60)">60</button>
        </span>
      </div>

      @if (event.acknowledgedAt) {
        <p class="ack-indicator">
          Подтверждено
          @if (ackUserName()) {
            — {{ ackUserName() }}
          }
          · {{ event.acknowledgedAt | date:'short' }}
        </p>
      }
      @if (silenceActive()) {
        <p class="silence-indicator">Скрыто до {{ event.silencedUntil | date:'short' }}</p>
      }

      @if (actionError()) {
        <div class="error">{{ actionError() }}</div>
      }
    </div>

    @if (commentOpen()) {
      <div class="overlay" (click)="closeComment()">
        <div class="modal" (click)="$event.stopPropagation()">
          <h2>Комментарий</h2>
          <textarea [(ngModel)]="commentText" name="comment" rows="4"></textarea>
          <div class="modal-actions">
            <button type="button" class="action-btn" (click)="closeComment()">Отмена</button>
            <button type="button" class="action-btn" [disabled]="busy()" (click)="submitComment()">Отправить</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .actions { display: flex; gap: 0.5rem; margin-bottom: 0.75rem; flex-wrap: wrap; align-items: center; }
      .action-btn { padding: 0.5rem 1rem; background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 6px; color: var(--text-secondary); cursor: pointer; font-size: 0.8125rem; }
      .action-btn:hover:not(:disabled) { border-color: var(--accent); color: var(--accent); }
      .action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
      .assign-wrap { display: flex; align-items: center; gap: 0.375rem; font-size: 0.8125rem; color: var(--text-secondary); }
      .assign-select { padding: 0.375rem 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-primary); color: var(--text-primary); }
      .silence-group { display: flex; align-items: center; gap: 0.375rem; }
      .silence-label { font-size: 0.8125rem; color: var(--text-secondary); }
      .ack-indicator, .silence-indicator { margin: 0 0 0.5rem; font-size: 0.75rem; color: var(--text-muted); }
      .error { color: #f44336; font-size: 0.8125rem; margin-bottom: 0.5rem; }
      .overlay { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.55); display: flex; align-items: center; justify-content: center; z-index: 20; }
      .modal { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; min-width: 320px; }
      .modal h2 { margin: 0 0 0.75rem; font-size: 1rem; color: var(--text-primary); }
      textarea { display: block; width: 100%; box-sizing: border-box; padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-primary); color: var(--text-primary); font: inherit; }
      .modal-actions { display: flex; justify-content: flex-end; gap: 0.5rem; margin-top: 0.75rem; }
    `,
  ],
})
export class EventDutyActionsComponent implements OnInit {
  private readonly api = inject(FmApiService);

  @Input({ required: true }) event!: FmEvent;
  @Input() disabled = false;
  @Output() readonly acted = new EventEmitter<EventActionResult>();

  readonly users = signal<User[]>([]);
  readonly acting = signal(false);
  readonly actionError = signal('');
  readonly commentOpen = signal(false);
  commentText = '';

  ngOnInit(): void {
    this.api.listUsers({ active: true }).subscribe((rows) => this.users.set(rows));
  }

  busy(): boolean {
    return this.disabled || this.acting();
  }

  ackUserName(): string {
    const id = this.event.acknowledgedByUserId;
    if (!id) return '';
    return this.users().find((u) => u.id === id)?.fullName ?? '';
  }

  silenceActive(): boolean {
    const until = this.event.silencedUntil;
    return !!until && new Date(until).getTime() > Date.now();
  }

  openComment(): void {
    this.commentText = '';
    this.commentOpen.set(true);
  }

  closeComment(): void {
    this.commentOpen.set(false);
  }

  submitComment(): void {
    const text = this.commentText.trim();
    if (!text) return;
    this.run('comment', { comment: text });
  }

  onAssign(ev: globalThis.Event): void {
    const userId = (ev.target as HTMLSelectElement).value;
    if (!userId) return;
    this.run('assign', { assignedUserId: userId });
  }

  silence(minutes: 15 | 30 | 60): void {
    this.run('silence', { silenceMinutes: minutes });
  }

  run(action: EventActionType, extra?: { comment?: string; assignedUserId?: string; silenceMinutes?: number }): void {
    this.acting.set(true);
    this.actionError.set('');
    const request$ = extra
      ? this.api.performEventAction(this.event.id, action, extra)
      : this.api.performEventAction(this.event.id, action);
    request$.subscribe({
      next: (result) => {
        this.acting.set(false);
        this.commentOpen.set(false);
        this.commentText = '';
        this.acted.emit(result);
      },
      error: () => {
        this.acting.set(false);
        this.actionError.set('Не удалось выполнить действие');
      },
    });
  }
}

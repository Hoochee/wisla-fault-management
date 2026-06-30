import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FmApiService } from '../../core/api/fm-api.service';
import { formatApiError } from '../../core/api/api-error';
import { ProcessingRule } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

@Component({
  selector: 'app-rules',
  standalone: true,
  imports: [RouterLink, DatePipe, PageHeaderComponent],
  template: `
    <app-page-header title="Правила обработки" subtitle="Дедуп, problem-resolution, корреляция" actionLabel="+ Создать" actionLink="/rules/new" />
    @if (actionMessage()) {
      <p class="action-msg">{{ actionMessage() }}</p>
    }
    @if (actionError()) {
      <p class="error">{{ actionError() }}</p>
    }
    <table class="data-table">
      <thead>
        <tr><th>Название</th><th>Тип</th><th>Триггер</th><th>Вкл</th><th>Согласование</th><th>Последний запуск</th><th></th></tr>
      </thead>
      <tbody>
        @for (r of rules; track r.id) {
          <tr>
            <td><a [routerLink]="['/rules', r.id]">{{ r.name }}</a></td>
            <td>{{ r.ruleType }}</td>
            <td>{{ r.triggerType }}</td>
            <td>
              <button
                type="button"
                [class]="r.enabled ? 'btn-secondary' : 'btn-primary'"
                [disabled]="togglingId() === r.id"
                (click)="toggleEnabled(r, $event)"
              >
                {{ togglingId() === r.id ? '…' : (r.enabled ? 'Выключить' : 'Включить') }}
              </button>
            </td>
            <td>{{ r.approvalStatus }}</td>
            <td class="mono">{{ r.lastRunAt ? (r.lastRunAt | date:'short') : '—' }}</td>
            <td><a [routerLink]="['/rules', r.id]">Изменить</a></td>
          </tr>
        }
      </tbody>
    </table>
  `,
  styles: [
    `
      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }
      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }
      .mono { font-family: var(--font-mono); font-size: 0.75rem; }
      a { color: var(--accent); text-decoration: none; }
      .btn-primary,
      .btn-secondary {
        padding: 0.35rem 0.65rem;
        border-radius: 6px;
        font-size: 0.75rem;
        border: none;
        cursor: pointer;
        white-space: nowrap;
      }
      .btn-primary {
        background: var(--accent);
        color: #fff;
      }
      .btn-primary:disabled,
      .btn-secondary:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .btn-secondary {
        background: var(--bg-sidebar);
        border: 1px solid var(--border);
        color: var(--text-secondary);
      }
      .action-msg {
        font-size: 0.8125rem;
        color: #4ade80;
        margin: 0 0 0.75rem;
      }
      .error {
        color: #f87171;
        font-size: 0.8125rem;
        margin: 0 0 0.75rem;
      }
    `,
  ],
})
export class RulesPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  rules: ProcessingRule[] = [];
  togglingId = signal<string | null>(null);
  actionMessage = signal('');
  actionError = signal('');

  ngOnInit(): void {
    this.api.listRules().subscribe((r) => (this.rules = r));
  }

  toggleEnabled(rule: ProcessingRule, event: Event): void {
    event.stopPropagation();
    const next = !rule.enabled;
    this.togglingId.set(rule.id);
    this.actionMessage.set('');
    this.actionError.set('');
    this.api.patchRule(rule.id, { enabled: next }).subscribe({
      next: (updated) => {
        this.rules = this.rules.map((r) => (r.id === rule.id ? { ...r, ...updated } : r));
        this.actionMessage.set(
          next ? `«${rule.name}» включено` : `«${rule.name}» выключено`,
        );
        this.togglingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.togglingId.set(null);
        this.actionError.set(formatApiError(err, 'Не удалось изменить состояние правила'));
      },
    });
  }
}

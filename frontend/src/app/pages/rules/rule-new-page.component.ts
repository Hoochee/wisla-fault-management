import { Component, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { FmApiService } from '../../core/api/fm-api.service';
import { formatApiError } from '../../core/api/api-error';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { RuleBuilderComponent } from '../../shared/rule-builder/rule-builder.component';
import { editorToCanvas, triggerTypeFromBlocks } from '../../shared/rule-builder/rule-canvas-mapper';

@Component({
  selector: 'app-rule-new',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, RuleBuilderComponent],
  template: `
    <app-page-header title="Новое правило" subtitle="Визуальный конструктор (Monq BP-стиль)" />
    <app-rule-builder #builder />
    @if (error) {
      <p class="error">{{ error }}</p>
    }
    <div class="actions">
      <a routerLink="/rules" class="btn-secondary">Отмена</a>
      <button type="button" class="btn-primary" [disabled]="saving" (click)="save()">
        {{ saving ? 'Сохранение…' : 'Сохранить' }}
      </button>
    </div>
  `,
  styles: [
    `
      .actions {
        display: flex;
        gap: 0.75rem;
        margin-top: 1rem;
      }
      .btn-primary,
      .btn-secondary {
        padding: 0.5rem 1rem;
        border-radius: 6px;
        font-size: 0.875rem;
        text-decoration: none;
        border: none;
        cursor: pointer;
      }
      .btn-primary {
        background: var(--accent);
        color: #fff;
      }
      .btn-primary:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .btn-secondary {
        background: var(--bg-sidebar);
        border: 1px solid var(--border);
        color: var(--text-secondary);
      }
      .error {
        color: #f87171;
        font-size: 0.8125rem;
        margin-top: 0.75rem;
      }
    `,
  ],
})
export class RuleNewPageComponent {
  private readonly api = inject(FmApiService);
  private readonly router = inject(Router);

  @ViewChild('builder') builder!: RuleBuilderComponent;

  saving = false;
  error = '';

  save(): void {
    const meta = this.builder.getMeta();
    if (!meta.name.trim()) {
      this.error = 'Укажите название правила';
      return;
    }
    this.saving = true;
    this.error = '';
    const blocks = this.builder.getBlocks();
    const canvas = editorToCanvas(blocks, this.builder.getConnections());
    this.api
      .createRule({
        name: meta.name.trim(),
        ruleType: meta.ruleType,
        triggerType: triggerTypeFromBlocks(blocks),
        description: meta.description.trim() || undefined,
        canvas,
      })
      .subscribe({
        next: (rule) => {
          this.saving = false;
          void this.router.navigate(['/rules', rule.id]);
        },
        error: (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = formatApiError(err, 'Не удалось создать правило');
        },
      });
  }
}

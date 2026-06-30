import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FmApiService } from '../../core/api/fm-api.service';
import { formatApiError } from '../../core/api/api-error';
import { ProcessingRuleDetail } from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { RuleBuilderComponent } from '../../shared/rule-builder/rule-builder.component';
import { editorToCanvas, triggerTypeFromBlocks } from '../../shared/rule-builder/rule-canvas-mapper';

@Component({
  selector: 'app-rule-edit',
  standalone: true,
  imports: [RouterLink, PageHeaderComponent, RuleBuilderComponent],
  template: `
    @if (rule) {
      <app-page-header [title]="rule.name" [subtitle]="rule.description" />
      <div class="meta">
        <span>Тип: {{ rule.ruleType }}</span>
        <span>Триггер: {{ rule.triggerType }}</span>
        <span>Статус: {{ rule.approvalStatus }}</span>
        <span>{{ rule.enabled ? 'Включено' : 'Выключено' }}</span>
      </div>
      <app-rule-builder
        #builder
        [showMeta]="true"
        [initialCanvas]="rule.canvas"
        [initialMeta]="{ name: rule.name, description: rule.description, ruleType: rule.ruleType }"
      />
      @if (error) {
        <p class="error">{{ error }}</p>
      }
      @if (saved) {
        <p class="success">Сохранено</p>
      }
      <div class="actions">
        <a routerLink="/rules" class="btn-secondary">← К списку</a>
        <button
          type="button"
          [class]="rule.enabled ? 'btn-secondary' : 'btn-primary'"
          [disabled]="togglingEnabled || saving"
          (click)="toggleEnabled()"
        >
          {{ togglingEnabled ? '…' : (rule.enabled ? 'Выключить' : 'Включить') }}
        </button>
        <button type="button" class="btn-primary" [disabled]="saving || togglingEnabled" (click)="save()">
          {{ saving ? 'Сохранение…' : 'Сохранить' }}
        </button>
      </div>
    }
  `,
  styles: [
    `
      .meta {
        display: flex;
        gap: 1.5rem;
        margin-bottom: 1rem;
        font-size: 0.8125rem;
        color: var(--text-muted);
        flex-wrap: wrap;
      }
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
      .success {
        color: #4ade80;
        font-size: 0.8125rem;
        margin-top: 0.75rem;
      }
    `,
  ],
})
export class RuleEditPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('builder') builder?: RuleBuilderComponent;

  rule: ProcessingRuleDetail | null = null;
  saving = false;
  togglingEnabled = false;
  error = '';
  saved = false;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.api.getRule(id).subscribe((r) => (this.rule = r));
  }

  toggleEnabled(): void {
    if (!this.rule) return;
    const next = !this.rule.enabled;
    this.togglingEnabled = true;
    this.error = '';
    this.saved = false;
    this.api.patchRule(this.rule.id, { enabled: next }).subscribe({
      next: (updated) => {
        this.rule = { ...this.rule!, ...updated };
        this.togglingEnabled = false;
        this.saved = true;
      },
      error: (err: HttpErrorResponse) => {
        this.togglingEnabled = false;
        this.error = formatApiError(err, 'Не удалось изменить состояние правила');
      },
    });
  }

  save(): void {
    if (!this.rule || !this.builder) return;
    const meta = this.builder.getMeta();
    if (!meta.name.trim()) {
      this.error = 'Укажите название правила';
      return;
    }
    this.saving = true;
    this.error = '';
    this.saved = false;
    const blocks = this.builder.getBlocks();
    const canvas = editorToCanvas(blocks, this.builder.getConnections());
    this.api
      .patchRule(this.rule.id, {
        name: meta.name.trim(),
        description: meta.description.trim() || undefined,
        triggerType: triggerTypeFromBlocks(blocks),
        canvas,
      })
      .subscribe({
        next: (updated) => {
          this.rule = updated;
          this.saving = false;
          this.saved = true;
        },
        error: (err: HttpErrorResponse) => {
          this.saving = false;
          this.error = formatApiError(err, 'Не удалось сохранить правило');
        },
      });
  }
}

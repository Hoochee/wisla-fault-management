import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { switchMap, tap } from 'rxjs';
import { FmApiService } from '../../core/api/fm-api.service';
import { setIngestApiKey } from '../../core/api/source-credentials.storage';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

interface CredentialsData {
  sourceId: string;
  webhookUrl: string;
  apiKey: string;
}

@Component({
  selector: 'app-source-new',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, PageHeaderComponent],
  template: `
    <app-page-header title="Новый источник" subtitle="Создание Push/Pull источника" />

    <form class="form" [formGroup]="form" (ngSubmit)="onSubmit()">
      <label>
        Название
        <input type="text" formControlName="name" placeholder="Zabbix Main" />
      </label>
      <label>
        Тип
        <select formControlName="type">
          <option value="push_rest">Push REST</option>
          <option value="pull_etl">Pull ETL</option>
        </select>
      </label>
      <label>
        Протокол
        <input type="text" formControlName="protocol" placeholder="HTTP" />
      </label>
      <label>
        Endpoint
        <input type="text" formControlName="endpoint" placeholder="https://zabbix.example.com/api" />
      </label>
      @if (error) {
        <div class="error">{{ error }}</div>
      }
      <div class="actions">
        <a routerLink="/sources" class="btn-secondary">Отмена</a>
        <button type="submit" class="btn-primary" [disabled]="form.invalid || saving">Создать</button>
      </div>
    </form>

    @if (credentials) {
      <div class="overlay">
        <div class="modal" (click)="$event.stopPropagation()">
          <h2>Данные для подключения</h2>
          <p class="warning">Сохраните ключ — повторно он не отображается.</p>
          <label>
            Webhook URL
            <div class="copy-row">
              <input type="text" readonly [value]="credentials.webhookUrl" />
              <button type="button" class="btn-copy" (click)="copy(credentials.webhookUrl, 'webhook')">Копировать</button>
            </div>
          </label>
          <label>
            API-ключ
            <div class="copy-row">
              <input type="text" readonly [value]="credentials.apiKey" />
              <button type="button" class="btn-copy" (click)="copy(credentials.apiKey, 'key')">Копировать</button>
            </div>
          </label>
          @if (copyHint) {
            <p class="copy-hint">{{ copyHint }}</p>
          }
          <div class="modal-actions">
            <button type="button" class="btn-primary" (click)="goToCard()">Перейти к карточке</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .form { max-width: 480px; display: flex; flex-direction: column; gap: 1rem; }
      label { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.8125rem; color: var(--text-secondary); }
      input, select { padding: 0.5rem; border-radius: 6px; border: 1px solid var(--border); background: var(--bg-sidebar); color: var(--text-primary); }
      .actions { display: flex; gap: 0.75rem; margin-top: 0.5rem; }
      .btn-primary, .btn-secondary, .btn-copy { padding: 0.5rem 1rem; border-radius: 6px; font-size: 0.875rem; text-decoration: none; border: none; cursor: pointer; }
      .btn-primary { background: var(--accent); color: #fff; }
      .btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
      .btn-secondary { background: var(--bg-sidebar); border: 1px solid var(--border); color: var(--text-secondary); }
      .btn-copy { background: var(--bg-sidebar); border: 1px solid var(--border); color: var(--text-secondary); white-space: nowrap; }
      .error { color: #f44336; font-size: 0.8125rem; }
      .overlay {
        position: fixed; inset: 0; background: rgba(0, 0, 0, 0.6);
        display: flex; align-items: center; justify-content: center; z-index: 1000;
      }
      .modal {
        background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 8px;
        padding: 1.25rem; width: min(32rem, 92vw); display: flex; flex-direction: column; gap: 0.75rem;
      }
      .modal h2 { margin: 0; font-size: 1rem; color: #fff; }
      .warning { margin: 0; font-size: 0.8125rem; color: #ffc107; }
      .copy-row { display: flex; gap: 0.5rem; }
      .copy-row input { flex: 1; font-family: var(--font-mono); font-size: 0.75rem; }
      .copy-hint { margin: 0; font-size: 0.75rem; color: #4caf50; }
      .modal-actions { display: flex; justify-content: flex-end; margin-top: 0.5rem; }
    `,
  ],
})
export class SourceNewPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(FmApiService);
  private readonly router = inject(Router);

  saving = false;
  error = '';
  credentials: CredentialsData | null = null;
  copyHint = '';

  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    type: ['push_rest', Validators.required],
    protocol: ['HTTP', Validators.required],
    endpoint: ['http://localhost:8081/webhook', Validators.required],
    status: ['inactive'],
  });

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.error = '';
    this.api
      .createSource(this.form.getRawValue())
      .pipe(
        switchMap((created) =>
          this.api.getSource(created.id).pipe(
            tap((detail) => {
              const apiKey = created.apiKey;
              if (!apiKey) {
                throw new Error('missing_api_key');
              }
              setIngestApiKey(created.id, apiKey);
              this.credentials = {
                sourceId: created.id,
                webhookUrl: detail.webhookUrl ?? '—',
                apiKey,
              };
            }),
          ),
        ),
      )
      .subscribe({
        next: () => {
          this.saving = false;
        },
        error: () => {
          this.error = 'Не удалось создать источник';
          this.saving = false;
        },
      });
  }

  copy(text: string, field: 'webhook' | 'key'): void {
    void navigator.clipboard.writeText(text).then(
      () => {
        this.copyHint = field === 'webhook' ? 'Webhook URL скопирован' : 'API-ключ скопирован';
      },
      () => {
        this.copyHint = 'Не удалось скопировать';
      },
    );
  }

  goToCard(): void {
    if (!this.credentials) return;
    void this.router.navigate(['/sources', this.credentials.sourceId]);
  }
}

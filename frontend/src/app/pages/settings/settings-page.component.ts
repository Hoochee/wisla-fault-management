import { Component, inject, OnInit } from '@angular/core';
import { forkJoin } from 'rxjs';
import { FmApiService } from '../../core/api/fm-api.service';
import {
  IntegrationSettings,
  NotificationSettings,
  SettingsBundle,
} from '../../core/api/api.models';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [PageHeaderComponent],
  template: `
    <app-page-header title="Настройки" subtitle="Параметры модуля и профиль пользователя" />
    @if (settings) {
      <div class="grid">
        <section class="card">
          <h2>Модуль</h2>
          <dl>
            <dt>Часовой пояс</dt><dd>{{ settings.module.timezone }}</dd>
            <dt>Интервал polling</dt><dd>{{ settings.module.pollingIntervalSec }} с</dd>
            <dt>Автоархив</dt><dd>{{ settings.module.autoArchiveDays }} дней</dd>
            <dt>Интервал повтора</dt><dd>{{ settings.module.repeatIntervalMin }} мин</dd>
            <dt>WISLA интеграция</dt><dd>{{ settings.module.wislaIntegration ? 'Вкл' : 'Выкл' }}</dd>
            <dt>ITSM интеграция</dt><dd>{{ settings.module.itsmIntegration ? 'Вкл' : 'Выкл' }}</dd>
          </dl>
        </section>
        <section class="card">
          <h2>Профиль</h2>
          @if (user()) {
            <dl>
              <dt>ФИО</dt><dd>{{ user()!.fullName }}</dd>
              <dt>Логин</dt><dd class="mono">{{ user()!.login }}</dd>
              <dt>Email</dt><dd>{{ user()!.email }}</dd>
              <dt>Команда</dt><dd>{{ user()!.team }}</dd>
            </dl>
          }
        </section>
        @if (notifications) {
          <section class="card">
            <h2>Уведомления</h2>
            <dl>
              <dt>Email</dt><dd>{{ notifications.emailEnabled ? 'Вкл' : 'Выкл' }}</dd>
              <dt>Telegram</dt><dd>{{ notifications.telegramEnabled ? 'Вкл' : 'Выкл' }}</dd>
              <dt>Правил</dt><dd>{{ notifications.rules?.length ?? 0 }}</dd>
            </dl>
          </section>
        }
        @if (integrations) {
          <section class="card">
            <h2>Интеграции</h2>
            <dl>
              <dt>WISLA</dt>
              <dd>{{ integrations.wisla?.enabled ? 'Вкл' : 'Выкл' }} · {{ integrations.wisla?.baseUrl ?? '—' }}</dd>
              <dt>ITSM</dt>
              <dd>{{ integrations.itsm?.enabled ? 'Вкл' : 'Выкл' }} · {{ integrations.itsm?.endpoint ?? '—' }}</dd>
            </dl>
          </section>
        }
      </div>
    }
  `,
  styles: [
    `
      .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
      @media (max-width: 768px) { .grid { grid-template-columns: 1fr; } }
      .card { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .card h2 { margin: 0 0 0.75rem; font-size: 0.9375rem; color: #fff; }
      dl { display: grid; grid-template-columns: 160px 1fr; gap: 0.5rem; font-size: 0.8125rem; margin: 0; }
      dt { color: var(--text-muted); }
      dd { margin: 0; color: var(--text-secondary); }
      .mono { font-family: var(--font-mono); }
    `,
  ],
})
export class SettingsPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  private readonly auth = inject(AuthService);
  settings: SettingsBundle | null = null;
  notifications: NotificationSettings | null = null;
  integrations: IntegrationSettings | null = null;
  readonly user = this.auth.currentUser;

  ngOnInit(): void {
    forkJoin({
      settings: this.api.getSettings(),
      notifications: this.api.getNotificationSettings(),
      integrations: this.api.getIntegrationSettings(),
    }).subscribe(({ settings, notifications, integrations }) => {
      this.settings = settings;
      this.notifications = notifications;
      this.integrations = integrations;
    });
    if (!this.user()) this.auth.loadCurrentUser().subscribe();
  }
}

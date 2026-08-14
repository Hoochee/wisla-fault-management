import { SlicePipe } from '@angular/common';
import { Component, Input, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigurationItem, Event, ProductHealth } from '../../core/api/api.models';
import { CiHealthProfile } from '../../core/health/health.models';
import { CiHealthTabComponent } from './ci-health-tab.component';
import { HealthBadgeComponent } from './health-badge.component';
import { SeverityBadgeComponent } from '../severity-badge/severity-badge.component';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';

type Tab = 'signals' | 'health' | 'params' | 'events';

@Component({
  selector: 'app-operative-center-panel',
  standalone: true,
  imports: [
    SlicePipe,
    RouterLink,
    CiHealthTabComponent,
    HealthBadgeComponent,
    SeverityBadgeComponent,
    StatusBadgeComponent,
  ],
  template: `
    @if (selectedCi && health) {
      <div class="panel">
        <div class="header">
          <div>
            <div class="subtitle">Оперативный центр · {{ product.name }}</div>
            <h2 class="fqdn">{{ selectedCi.fqdn }}</h2>
            <div class="meta">{{ selectedCi.system }} · {{ selectedCi.ciType }}</div>
          </div>
          <app-health-badge [level]="health.currentHealth" [percent]="health.healthPercent" />
        </div>

        <div class="tabs">
          @for (tab of tabs; track tab.id) {
            <button type="button" class="tab" [class.active]="activeTab === tab.id" (click)="activeTab = tab.id">
              {{ tab.label }}
              @if (tab.id === 'signals' && totalSignals > 0) {
                <span class="badge-count">{{ totalSignals }}</span>
              }
            </button>
          }
        </div>

        <div class="content">
          @if (activeTab === 'health') {
            <app-ci-health-tab [ci]="selectedCi" [health]="health" />
          }
          @if (activeTab === 'signals') {
            <div class="card">
              <p class="muted">Активные сигналы по КЕ — {{ totalSignals }} шт.</p>
              <div class="signal-list">
                @for (e of ciActiveEvents; track e.id) {
                  <a [routerLink]="['/console', e.id]" class="signal-row">
                    <app-severity-badge [severity]="e.severity" [compact]="true" />
                    <span class="title">{{ e.title }}</span>
                    <app-status-badge [status]="e.status" />
                  </a>
                }
                @if (ciActiveEvents.length === 0) {
                  <p class="muted">Нет активных сигналов</p>
                }
              </div>
              <a [routerLink]="['/console']" [queryParams]="{ ci: selectedCi.id }" class="link">Открыть в консоли →</a>
            </div>
          }
          @if (activeTab === 'params') {
            <div class="card">
              <h3>Параметры КЕ</h3>
              <dl class="params">
                <div><dt>FQDN</dt><dd>{{ selectedCi.fqdn }}</dd></div>
                <div><dt>Тип</dt><dd>{{ selectedCi.ciType }}</dd></div>
                <div><dt>Система</dt><dd>{{ selectedCi.system }}</dd></div>
                <div><dt>Подсистема</dt><dd>{{ selectedCi.subsystem }}</dd></div>
                <div><dt>ПО</dt><dd>{{ selectedCi.software }}</dd></div>
                <div><dt>Тенант</dt><dd>{{ product.tenant }}</dd></div>
                <div><dt>Площадка</dt><dd>{{ product.site }}</dd></div>
              </dl>
              <div class="tags">
                @for (tag of allTags; track tag) {
                  <span class="tag">{{ tag }}</span>
                }
              </div>
            </div>
          }
          @if (activeTab === 'events') {
            <div class="card">
              <div class="card-head">
                <h3>События КЕ</h3>
                <a [routerLink]="['/console']" [queryParams]="{ ci: selectedCi.id }" class="link">Консоль →</a>
              </div>
              <div class="signal-list">
                @for (e of ciAllEvents; track e.id) {
                  <a [routerLink]="['/console', e.id]" class="event-row">
                    <app-status-badge [status]="e.status" />
                    <app-severity-badge [severity]="e.severity" [compact]="true" />
                    <span class="title">{{ e.title }}</span>
                    <span class="time">{{ e.createdAt | slice: 0 : 16 }}</span>
                  </a>
                }
              </div>
            </div>
          }
        </div>
      </div>
    } @else {
      <p class="muted">Выберите КЕ</p>
    }
  `,
  styles: [
    `
      .panel { display: flex; flex-direction: column; height: 100%; min-height: 0; }
      .header { display: flex; justify-content: space-between; margin-bottom: 1rem; }
      .subtitle { font-size: 0.75rem; color: var(--text-muted); }
      .fqdn { margin: 0.125rem 0 0; font-size: 1.125rem; font-weight: 600; color: var(--text-primary); font-family: var(--font-mono); }
      .meta { font-size: 0.75rem; color: var(--text-muted); margin-top: 0.125rem; }
      .tabs { display: flex; border-bottom: 1px solid var(--border); margin-bottom: 1rem; }
      .tab {
        padding: 0.5rem 1rem; font-size: 0.875rem; background: none; border: none; border-bottom: 2px solid transparent;
        margin-bottom: -1px; color: var(--text-muted); cursor: pointer;
      }
      .tab:hover { color: var(--text-primary); }
      .tab.active { border-bottom-color: var(--accent); color: var(--accent); }
      .badge-count { margin-left: 0.375rem; padding: 0 0.375rem; font-size: 10px; border-radius: 4px; background: var(--danger); color: #fff; }
      .content { flex: 1; overflow: auto; min-height: 0; }
      .card { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .card h3 { margin: 0 0 0.75rem; font-size: 0.875rem; color: var(--text-primary); font-weight: 500; }
      .card-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
      .card-head h3 { margin: 0; }
      .muted { font-size: 0.875rem; color: var(--text-muted); }
      .signal-list { display: flex; flex-direction: column; gap: 0.5rem; margin: 1rem 0; }
      .signal-row, .event-row {
        display: flex; align-items: center; gap: 0.5rem; padding: 0.5rem 0.75rem; border-radius: 8px;
        border: 1px solid var(--border); text-decoration: none; font-size: 0.875rem;
      }
      .signal-row:hover, .event-row:hover { background: var(--nav-hover); }
      .title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--text-primary); }
      .time { font-size: 0.75rem; color: var(--text-muted); }
      .link { font-size: 0.75rem; color: var(--accent); text-decoration: none; }
      .link:hover { text-decoration: underline; }
      .params { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; font-size: 0.875rem; }
      .params dt { font-size: 0.75rem; color: var(--text-muted); }
      .params dd { margin: 0.125rem 0 0; color: var(--text-primary); }
      .tags { display: flex; flex-wrap: wrap; gap: 0.25rem; margin-top: 1rem; }
      .tag { padding: 0.125rem 0.5rem; font-size: 0.75rem; border-radius: 4px; background: var(--bg-primary); color: var(--text-secondary); }
    `,
  ],
})
export class OperativeCenterPanelComponent implements OnInit {
  @Input({ required: true }) product!: ProductHealth;
  @Input({ required: true }) ciList!: ConfigurationItem[];
  @Input({ required: true }) selectedCiId!: string;
  @Input({ required: true }) profiles!: Record<string, CiHealthProfile>;
  @Input({ required: true }) events!: Event[];
  @Input() defaultTab: Tab = 'health';

  activeTab: Tab = 'health';

  ngOnInit(): void {
    this.activeTab = this.defaultTab;
  }

  readonly tabs: { id: Tab; label: string }[] = [
    { id: 'signals', label: 'Сигналы' },
    { id: 'health', label: 'Здоровье' },
    { id: 'params', label: 'Параметры' },
    { id: 'events', label: 'События' },
  ];

  get selectedCi(): ConfigurationItem | undefined {
    return this.ciList.find((c) => c.id === this.selectedCiId) ?? this.ciList[0];
  }

  get health(): CiHealthProfile | undefined {
    const ci = this.selectedCi;
    return ci ? this.profiles[ci.id] : undefined;
  }

  get ciActiveEvents(): Event[] {
    const ci = this.selectedCi;
    if (!ci) return [];
    return this.events.filter((e) => e.ciId === ci.id && e.status !== 'closed' && e.status !== 'archived');
  }

  get ciAllEvents(): Event[] {
    const ci = this.selectedCi;
    if (!ci) return [];
    return this.events.filter((e) => e.ciId === ci.id).slice(0, 10);
  }

  get totalSignals(): number {
    return this.health ? Object.values(this.health.signalsBySeverity).reduce((a, b) => a + b, 0) : 0;
  }

  get allTags(): string[] {
    const ci = this.selectedCi;
    if (!ci) return this.product.tags;
    return [...new Set([...(ci.tags ?? []), ...(this.product.tags ?? [])])];
  }
}

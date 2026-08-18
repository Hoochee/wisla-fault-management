import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfigurationItem, Severity } from '../../core/api/api.models';
import { CiHealthProfile } from '../../core/health/health.models';
import { HealthBadgeComponent } from './health-badge.component';
import { HealthTimelineChartComponent } from './health-timeline-chart.component';
import { SeverityBadgeComponent } from '../severity-badge/severity-badge.component';

@Component({
  selector: 'app-ci-health-tab',
  standalone: true,
  imports: [RouterLink, HealthBadgeComponent, HealthTimelineChartComponent, SeverityBadgeComponent],
  template: `
    <div class="root">
      <div class="summary card">
        <div class="summary-main">
          <div class="percent-box" [attr.data-level]="health.currentHealth">
            {{ health.healthPercent }}%
          </div>
          <div>
            <div class="label">Текущее здоровье КЕ</div>
            <app-health-badge [level]="health.currentHealth" [percent]="health.healthPercent" />
            <div class="fqdn">{{ ci.fqdn }}</div>
            <div class="meta">{{ ci.system }} · {{ ci.ciType }}</div>
          </div>
        </div>
        <div class="summary-side">
          <div>
            <div class="label">Мин. за сутки</div>
            <app-health-badge [level]="health.minToday" [percent]="health.minTodayPercent" [compact]="true" />
          </div>
          <div>
            <div class="label">Макс. за сутки</div>
            <app-health-badge [level]="health.maxToday" [percent]="health.maxTodayPercent" [compact]="true" />
          </div>
          <button type="button" class="rca-btn" [class.open]="rcaOpen" (click)="rcaOpen = !rcaOpen">RCA</button>
        </div>
      </div>

      <div class="grid">
        <div class="card components">
          <h3>Здоровье компонентов</h3>
          <table>
            <thead>
              <tr><th>Компонент</th><th>Здоровье</th><th class="num">Ущерб от компонентов</th><th class="num">Ущерб от влияния</th></tr>
            </thead>
            <tbody>
              @for (c of health.components; track c.id) {
                <tr>
                  <td>{{ c.name }}</td>
                  <td><app-health-badge [level]="c.health" [percent]="c.healthPercent" [compact]="true" /></td>
                  <td class="num">{{ c.damageFromComponents }}%</td>
                  <td class="num">{{ c.damageFromInfluence }}%</td>
                </tr>
              } @empty {
                <tr><td colspan="4" class="empty">Нет данных компонентов</td></tr>
              }
            </tbody>
          </table>
        </div>
        <div class="card signals">
          <div class="signals-head">
            <h3>Сигналы</h3>
            <span class="count">{{ totalSignals }}</span>
          </div>
          @for (s of severities; track s) {
            <div class="signal-row">
              <app-severity-badge [severity]="s" [compact]="true" />
              <span>{{ health.signalsBySeverity[s] }}</span>
            </div>
          }
          <a [routerLink]="['/console']" [queryParams]="{ productId: productId }" class="link">Перейти к сигналам →</a>
        </div>
      </div>

      @if (health.dependents.length) {
        <div class="card">
          <h3>Зависимые КЕ (влияние)</h3>
          @for (d of health.dependents; track d.id) {
            <div class="dependent">
              <span class="icon">{{ influenceIcon(d.influenceType) }}</span>
              <div class="dep-main">
                <div class="fqdn">{{ d.fqdn }}</div>
                @if (d.componentName) {
                  <div class="meta">Компонент: {{ d.componentName }}</div>
                }
              </div>
              <app-health-badge [level]="d.health" [percent]="d.healthPercent" [compact]="true" />
            </div>
          }
        </div>
      }

      @if (health.timeline.length) {
        <app-health-timeline-chart [timeline]="health.timeline" />
      }

      @if (rcaOpen) {
        <div class="rca-panel">
          <div class="rca-head">
            <h3>Root Cause Analysis (RCA)</h3>
            <button type="button" class="close" (click)="rcaOpen = false">×</button>
          </div>
          <div class="rca-toolbar">
            <label>
              Порог ущерба (%)
              <input type="number" min="0" max="100" [value]="damageThreshold" (input)="onThreshold($event)" />
            </label>
          </div>
          <div class="rca-body">
            <table>
              <thead>
                <tr><th>Источник</th><th>Тип</th><th class="num">Ущерб</th><th>Здоровье</th></tr>
              </thead>
              <tbody>
                @if (rcaRows.length === 0) {
                  <tr><td colspan="4" class="empty">Нет источников с ущербом ≥ {{ damageThreshold }}%</td></tr>
                }
                @for (r of rcaRows; track r.name + r.type) {
                  <tr>
                    <td>{{ r.name }}</td>
                    <td>{{ r.type }}</td>
                    <td class="num damage">{{ r.damage }}%</td>
                    <td><app-health-badge [level]="r.health" [percent]="r.percent" [compact]="true" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .root { display: flex; flex-direction: column; gap: 1rem; }
      .card { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .summary { display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }
      .summary-main { display: flex; gap: 1rem; align-items: flex-start; }
      .percent-box {
        width: 4rem; height: 4rem; border-radius: 8px; display: flex; align-items: center; justify-content: center;
        font-size: 1.5rem; font-weight: 700; border: 2px solid;
      }
      .percent-box[data-level='ok'] { border-color: #22c55e; background: rgba(34,197,94,0.12); color: #22c55e; }
      .percent-box[data-level='warning'] { border-color: #eab308; background: rgba(234,179,8,0.12); color: #eab308; }
      .percent-box[data-level='major'] { border-color: #ea580c; background: rgba(234,88,12,0.12); color: #ea580c; }
      .percent-box[data-level='fatal'], .percent-box[data-level='critical'] { border-color: #dc2626; background: rgba(220,38,38,0.12); color: #dc2626; }
      .label { font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.25rem; }
      .fqdn { font-family: var(--font-mono); font-size: 0.875rem; color: var(--text-primary); margin-top: 0.5rem; }
      .meta { font-size: 0.75rem; color: var(--text-muted); }
      .summary-side { display: flex; gap: 1.5rem; align-items: flex-start; font-size: 0.875rem; }
      .rca-btn {
        padding: 0.375rem 0.75rem; font-size: 0.75rem; border-radius: 6px; cursor: pointer;
        background: var(--bg-card); border: 1px solid var(--border); color: var(--text-primary);
      }
      .rca-btn.open { background: var(--nav-hover); border-color: var(--accent); color: var(--accent); }
      .grid { display: grid; grid-template-columns: 2fr 1fr; gap: 1rem; }
      @media (max-width: 1200px) { .grid { grid-template-columns: 1fr; } }
      h3 { margin: 0 0 0.75rem; font-size: 0.875rem; color: var(--text-primary); font-weight: 500; }
      table { width: 100%; font-size: 0.875rem; border-collapse: collapse; }
      th, td { padding: 0.5rem 0; border-bottom: 1px solid var(--border); text-align: left; }
      th { font-size: 0.75rem; color: var(--text-muted); font-weight: 400; }
      .num { text-align: right; color: var(--text-secondary); }
      .signals-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
      .signals-head h3 { margin: 0; }
      .count { font-size: 1.125rem; font-weight: 700; color: var(--text-primary); }
      .signal-row { display: flex; justify-content: space-between; font-size: 0.875rem; margin-bottom: 0.5rem; }
      .link { display: block; margin-top: 1rem; text-align: center; font-size: 0.75rem; color: var(--accent); text-decoration: none; }
      .dependent { display: flex; align-items: center; gap: 0.75rem; padding: 0.5rem 0.75rem; background: var(--bg-primary); border: 1px solid var(--border); border-radius: 8px; margin-bottom: 0.5rem; }
      .icon { font-size: 1rem; }
      .dep-main { flex: 1; min-width: 0; }
      .rca-panel {
        position: fixed; inset: auto 0 0 0; z-index: 40; background: var(--bg-card); border-top: 1px solid var(--border);
        box-shadow: 0 -8px 24px rgba(28,31,38,0.12); max-height: 50vh; display: flex; flex-direction: column;
      }
      .rca-head { display: flex; justify-content: space-between; padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); }
      .rca-head h3 { margin: 0; font-size: 0.875rem; color: var(--text-primary); }
      .close { background: none; border: none; color: var(--text-muted); font-size: 1.25rem; cursor: pointer; }
      .rca-toolbar { padding: 0.75rem 1rem; border-bottom: 1px solid var(--border); font-size: 0.75rem; color: var(--text-secondary); }
      .rca-toolbar input { margin-left: 0.5rem; width: 4rem; padding: 0.25rem 0.5rem; background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; color: var(--text-primary); }
      .rca-body { flex: 1; overflow: auto; padding: 0.5rem 1rem; }
      .empty { text-align: center; color: var(--text-muted); padding: 1rem; }
      .damage { color: #dc2626; }
    `,
  ],
})
export class CiHealthTabComponent {
  @Input({ required: true }) ci!: ConfigurationItem;
  @Input({ required: true }) health!: CiHealthProfile;
  @Input({ required: true }) productId!: string;

  rcaOpen = false;
  damageThreshold = 30;
  readonly severities: Severity[] = ['fatal', 'critical', 'major', 'minor', 'warning', 'normal'];

  get totalSignals(): number {
    return Object.values(this.health.signalsBySeverity).reduce((a, b) => a + b, 0);
  }

  get rcaRows() {
    return [
      ...this.health.components.map((c) => ({
        name: c.name,
        type: 'Компонент',
        damage: c.damageFromComponents + c.damageFromInfluence,
        health: c.health,
        percent: c.healthPercent,
      })),
      ...this.health.dependents.map((d) => ({
        name: d.fqdn,
        type: 'Влияние',
        damage: 100 - d.healthPercent,
        health: d.health,
        percent: d.healthPercent,
      })),
    ]
      .filter((r) => r.damage >= this.damageThreshold)
      .sort((a, b) => b.damage - a.damage);
  }

  influenceIcon(type: string): string {
    if (type === 'combo') return '⊕';
    if (type === 'weighted') return '⚖';
    return '⚠';
  }

  onThreshold(event: Event): void {
    this.damageThreshold = Number((event.target as HTMLInputElement).value);
  }
}

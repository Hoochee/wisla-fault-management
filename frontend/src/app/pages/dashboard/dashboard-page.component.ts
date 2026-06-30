import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FmApiService } from '../../core/api/fm-api.service';
import { DashboardSummary } from '../../core/api/api.models';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  template: `
    <h1 class="title">Оперативный центр</h1>
    @if (summary) {
      <div class="severity-grid">
        @for (item of severityItems; track item.key) {
          <a class="severity-card" [routerLink]="['/console']" [queryParams]="{ severity: item.key }">
            <span class="count" [style.color]="item.color">{{ item.count }}</span>
            <span class="label">{{ item.label }}</span>
          </a>
        }
      </div>
      <div class="panels">
        <section class="panel">
          <h2>Карты событий</h2>
          <div class="map-list">
            @for (map of summary.systemMaps; track map.id) {
              <a [routerLink]="['/console']" [queryParams]="{ mapId: map.id }" class="map-item">{{ map.name }}</a>
            }
          </div>
        </section>
        <section class="panel">
          <h2>Здоровье продуктов <a routerLink="/health" class="link">→</a></h2>
          <div class="product-grid">
            @for (p of summary.productPreview; track p.id) {
              <a [routerLink]="['/health', p.id]" class="product-cell" [attr.data-severity]="p.maxSeverity">
                <span class="product-name">{{ p.name }}</span>
                <span class="product-count">{{ p.activeEventCount }} событий</span>
              </a>
            }
          </div>
        </section>
      </div>
      <p class="total">Всего активных: <strong>{{ summary.totalActive }}</strong></p>
    }
  `,
  styles: [
    `
      .title { margin: 0 0 1.5rem; font-size: 1.5rem; color: #fff; }
      .severity-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 0.75rem; margin-bottom: 1.5rem; }
      .severity-card { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; text-decoration: none; text-align: center; }
      .severity-card:hover { border-color: var(--accent); }
      .count { display: block; font-size: 2rem; font-weight: 700; font-family: var(--font-mono); }
      .label { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; }
      .panels { display: grid; grid-template-columns: 1fr 2fr; gap: 1rem; }
      @media (max-width: 900px) { .panels { grid-template-columns: 1fr; } }
      .panel { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .panel h2 { margin: 0 0 0.75rem; font-size: 0.9375rem; color: #fff; }
      .link { color: var(--accent); text-decoration: none; font-size: 0.875rem; }
      .map-item { display: block; padding: 0.5rem; color: var(--text-secondary); text-decoration: none; border-radius: 4px; font-size: 0.875rem; }
      .map-item:hover { background: rgba(74,158,255,0.1); color: var(--accent); }
      .product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 0.5rem; }
      .product-cell { padding: 0.75rem; border-radius: 6px; text-decoration: none; border: 1px solid var(--border); }
      .product-cell[data-severity='critical'] { background: rgba(244,67,54,0.15); }
      .product-cell[data-severity='major'] { background: rgba(255,152,0,0.15); }
      .product-cell[data-severity='warning'] { background: rgba(255,235,59,0.1); }
      .product-name { display: block; color: #fff; font-size: 0.8125rem; font-weight: 500; }
      .product-count { font-size: 0.6875rem; color: var(--text-muted); }
      .total { margin-top: 1rem; color: var(--text-muted); font-size: 0.875rem; }
    `,
  ],
})
export class DashboardPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  summary: DashboardSummary | null = null;

  severityItems: { key: string; label: string; count: number; color: string }[] = [];

  ngOnInit(): void {
    this.api.getDashboardSummary().subscribe((s) => {
      this.summary = s;
      const colors: Record<string, string> = {
        critical: '#f44336', major: '#ff9800', minor: '#ffc107', warning: '#ffeb3b',
      };
      this.severityItems = [
        { key: 'critical', label: 'Critical', count: s.severityCounts.critical, color: colors['critical'] },
        { key: 'major', label: 'Major', count: s.severityCounts.major, color: colors['major'] },
        { key: 'minor', label: 'Minor', count: s.severityCounts.minor, color: colors['minor'] },
        { key: 'warning', label: 'Warning', count: s.severityCounts.warning, color: colors['warning'] },
      ];
    });
  }
}

import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FmApiService } from '../../core/api/fm-api.service';
import {
  ConfigurationItem,
  DashboardSummary,
  HealthLevel,
  ProductHealth,
  ProductHealthDetail,
} from '../../core/api/api.models';
import { buildProfiles, HEALTH_LABELS, percentToLevel } from '../../core/health/health-profile.util';

interface ProductTileCi {
  id: string;
  fqdn: string;
  ciType: string;
  healthPercent: number;
}

interface ProductTileView {
  id: string;
  name: string;
  healthPercent?: number;
  damagePercent?: number;
  minHealthToday?: number;
  maxHealthToday?: number;
  cis: ProductTileCi[];
}

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
      <section class="panel maps-section">
        <h2>Карты событий</h2>
        <div class="map-list">
          @for (map of summary.systemMaps; track map.id) {
            <a [routerLink]="['/console']" [queryParams]="{ mapId: map.id }" class="map-item">{{ map.name }}</a>
          }
        </div>
      </section>
      <section class="panel health-section">
        <h2>Здоровье продуктов <a routerLink="/health" class="link">→</a></h2>
        <div class="product-grid">
          @for (p of products; track p.id) {
            <a
              [routerLink]="['/health', p.id]"
              class="product-cell"
              [attr.data-level]="p.healthPercent == null ? 'unknown' : percentToLevel(p.healthPercent)"
            >
              <aside class="health-summary-card">
                <div class="card-kicker">
                  <span class="type-icon" aria-hidden="true">▣</span>
                  <span class="card-title product-name">{{ p.name }}</span>
                </div>
                <div class="health-score" [attr.data-level]="tileLevel(p)">
                  {{ p.healthPercent ?? '—' }}%
                </div>
                <div class="severity" [attr.data-level]="tileLevel(p)">{{ HEALTH_LABELS[tileLevel(p)] }}</div>
                <div class="damage-box">
                  <div class="damage-label">Анализ урона</div>
                  <div class="damage-value">{{ damageText(p) }}</div>
                </div>
                <div class="range">
                  Мин <strong>{{ p.minHealthToday ?? '—' }}%</strong>
                  —
                  Макс <strong>{{ p.maxHealthToday ?? '—' }}%</strong>
                </div>
              </aside>
              <table class="ci-table">
                <thead>
                  <tr>
                    <th>Название КЕ</th>
                    <th>Тип</th>
                    <th>Здоровье</th>
                  </tr>
                </thead>
                <tbody>
                  @for (ci of p.cis; track ci.id) {
                    <tr class="product-ci">
                      <td class="ci-label">{{ ci.fqdn }}</td>
                      <td class="ci-type">{{ ci.ciType }}</td>
                      <td class="ci-pct">{{ ci.healthPercent }}%</td>
                    </tr>
                  }
                </tbody>
              </table>
            </a>
          }
        </div>
      </section>
      <p class="total">Всего продуктов: <strong>{{ products.length }}</strong></p>
    }
  `,
  styles: [
    `
      .title { margin: 0 0 1.5rem; font-size: 1.5rem; color: var(--text-primary); }
      .severity-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 0.75rem; margin-bottom: 1.5rem; }
      .severity-card { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; text-decoration: none; text-align: center; }
      .severity-card:hover { border-color: var(--accent); }
      .count { display: block; font-size: 2rem; font-weight: 700; font-family: var(--font-mono); }
      .label { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; }
      .panel { background: var(--bg-sidebar); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; margin-bottom: 1rem; }
      .maps-section, .health-section { width: 100%; }
      .panel h2 { margin: 0 0 0.75rem; font-size: 0.9375rem; color: var(--text-primary); }
      .link { color: var(--accent); text-decoration: none; font-size: 0.875rem; }
      .map-item { display: block; padding: 0.5rem; color: var(--text-secondary); text-decoration: none; border-radius: 4px; font-size: 0.875rem; }
      .map-item:hover { background: var(--nav-hover); color: var(--accent); }
      .product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 0.75rem; }
      .product-cell {
        display: flex;
        flex-direction: column;
        padding: 0;
        border-radius: 8px;
        text-decoration: none;
        border: 1px solid var(--border);
        background: var(--bg-card, var(--bg-sidebar));
        overflow: hidden;
        color: inherit;
      }
      .health-summary-card {
        position: relative;
        width: auto;
        flex-shrink: 0;
        padding: 1.25rem 0.55rem 1.25rem 1.15rem;
        border-right: none;
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .card-kicker {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .type-icon {
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 8px;
        border: 1px solid var(--border);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        color: var(--text-secondary);
        background: var(--bg-primary);
      }
      .card-title {
        font-size: 0.9375rem;
        font-weight: 600;
        color: var(--text-primary);
      }
      .health-score {
        font-size: 2.25rem;
        font-weight: 700;
        line-height: 1.1;
        letter-spacing: -0.03em;
      }
      .health-score[data-level='ok'] { color: #22c55e; }
      .health-score[data-level='warning'] { color: #ca8a04; }
      .health-score[data-level='major'] { color: #ea580c; }
      .health-score[data-level='fatal'],
      .health-score[data-level='critical'] { color: #dc2626; }
      .health-score[data-level='unknown'] { color: var(--text-muted); }
      .severity {
        font-size: 0.875rem;
        font-weight: 600;
        margin-top: -0.25rem;
      }
      .severity[data-level='ok'] { color: #22c55e; }
      .severity[data-level='warning'] { color: #ca8a04; }
      .severity[data-level='major'] { color: #ea580c; }
      .severity[data-level='fatal'],
      .severity[data-level='critical'] { color: #dc2626; }
      .damage-box {
        margin-top: 0.5rem;
        padding: 0.65rem 0.75rem;
        border-radius: 8px;
        background: #f8e4d4;
        border: 1px solid #edc9a8;
      }
      .damage-label {
        font-size: 0.6875rem;
        color: #9a5a32;
        margin-bottom: 0.125rem;
      }
      .damage-value {
        font-size: 1.375rem;
        font-weight: 700;
        color: #c2410c;
        letter-spacing: -0.02em;
      }
      .range {
        font-size: 0.75rem;
        color: var(--text-secondary);
      }
      .range strong { color: var(--text-primary); font-weight: 600; }
      .ci-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.75rem;
        border-top: 1px solid var(--border);
        pointer-events: none;
      }
      .ci-table th,
      .ci-table td {
        padding: 0.4rem 0.65rem;
        text-align: left;
        vertical-align: baseline;
      }
      .ci-table th {
        font-size: 0.6875rem;
        font-weight: 600;
        color: var(--text-muted);
        text-transform: none;
      }
      .ci-table td { color: var(--text-secondary); }
      .ci-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 10rem; }
      .ci-type { color: var(--text-muted); }
      .ci-pct { font-weight: 600; color: var(--text-primary); font-variant-numeric: tabular-nums; }
      .total { margin-top: 1rem; color: var(--text-muted); font-size: 0.875rem; }
    `,
  ],
})
export class DashboardPageComponent implements OnInit {
  private readonly api = inject(FmApiService);
  summary: DashboardSummary | null = null;
  products: ProductTileView[] = [];

  severityItems: { key: string; label: string; count: number; color: string }[] = [];

  readonly percentToLevel = percentToLevel;
  readonly HEALTH_LABELS = HEALTH_LABELS;

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
    this.api.getProducts().subscribe({
      next: (rows) => {
        this.products = rows.map((row) => this.toTile(row));
        for (const row of rows) {
          this.api.getProduct(row.id).subscribe({
            next: (detail) => this.applyProductCis(row.id, detail),
            error: () => undefined,
          });
        }
      },
      error: () => {
        this.products = [];
      },
    });
  }

  tileLevel(p: ProductTileView): HealthLevel {
    return percentToLevel(p.healthPercent ?? 100);
  }

  damageText(p: ProductTileView): string {
    const d = p.damagePercent;
    if (d == null) return '—';
    const n = Number.isInteger(d) ? String(d) : d.toFixed(2);
    return `−${n}%`;
  }

  private toTile(row: ProductHealth): ProductTileView {
    return {
      id: row.id,
      name: row.name,
      healthPercent: row.healthPercent,
      damagePercent: row.damagePercent,
      cis: [],
    };
  }

  private applyProductCis(id: string, detail: ProductHealthDetail): void {
    const configurationItems: ConfigurationItem[] = detail.configurationItems ?? [];
    const profiles = buildProfiles(configurationItems, detail.activeEvents ?? []);
    const cis: ProductTileCi[] = configurationItems.map((item) => ({
      id: item.id,
      fqdn: item.fqdn,
      ciType: item.ciType,
      healthPercent: profiles[item.id]?.healthPercent ?? 100,
    }));
    this.products = this.products.map((p) =>
      p.id === id
        ? {
            ...p,
            healthPercent: detail.healthPercent ?? p.healthPercent,
            damagePercent: detail.damagePercent ?? p.damagePercent,
            minHealthToday: detail.minHealthToday,
            maxHealthToday: detail.maxHealthToday,
            cis,
          }
        : p,
    );
  }
}

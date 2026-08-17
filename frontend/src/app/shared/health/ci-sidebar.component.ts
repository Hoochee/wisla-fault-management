import { Component, Input, Output, EventEmitter } from '@angular/core';
import { ConfigurationItem, ProductHealth } from '../../core/api/api.models';
import { CiHealthProfile } from '../../core/health/health.models';
import { HealthBadgeComponent } from './health-badge.component';

@Component({
  selector: 'app-ci-sidebar',
  standalone: true,
  imports: [HealthBadgeComponent],
  template: `
    <div class="sidebar">
      <div class="product-name">{{ product.name }}</div>
      <div class="list">
        @for (ci of ciList; track ci.id) {
          <button
            type="button"
            class="ci-btn"
            [class.selected]="ci.id === selectedCiId"
            (click)="selectCi.emit(ci.id)"
          >
            <div class="fqdn">{{ ci.fqdn }}</div>
            @if (profiles[ci.id]; as h) {
              <app-health-badge [level]="h.currentHealth" [percent]="h.healthPercent" [compact]="true" />
            }
          </button>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host { display: block; }
      .sidebar { width: 14rem; flex-shrink: 0; border-right: 1px solid var(--border); padding-right: 1rem; }
      .product-name { font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.5rem; }
      .list { display: flex; flex-direction: column; gap: 0.25rem; }
      .ci-btn {
        width: 100%; text-align: left; padding: 0.5rem; border-radius: 8px; border: 1px solid transparent;
        background: transparent; cursor: pointer; transition: background 0.15s, border-color 0.15s;
      }
      .ci-btn:hover { background: var(--nav-hover); }
      .ci-btn.selected { background: var(--nav-hover); border-color: rgba(47, 111, 237, 0.35); }
      .fqdn { font-family: var(--font-mono); font-size: 0.75rem; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .ci-btn.selected .fqdn { color: var(--text-primary); }
    `,
  ],
})
export class CiSidebarComponent {
  @Input({ required: true }) product!: ProductHealth;
  @Input({ required: true }) ciList!: ConfigurationItem[];
  @Input({ required: true }) selectedCiId!: string;
  @Input({ required: true }) profiles!: Record<string, CiHealthProfile>;
  @Output() selectCi = new EventEmitter<string>();
}

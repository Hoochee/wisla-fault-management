import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  ConfigurationItem,
  ProductComponentAdmin,
  ProductComponentPatch,
} from '../../core/api/api.models';

interface ComponentDraft {
  code: string;
  name: string;
  weight: number;
  influenceType: string;
  criticalThreshold: number;
  ciIds: string[];
}

@Component({
  selector: 'app-component-weight-editor',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="panel">
      <div class="head">
        <h3>Веса компонентов</h3>
        @if (editable) {
          <button type="button" class="btn-primary" [disabled]="saving" (click)="save()">Сохранить</button>
        }
      </div>
      @if (displayError) {
        <p class="error">{{ displayError }}</p>
      }
      @if (drafts.length === 0) {
        <p class="empty">Нет компонентов</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Код</th>
              <th>Название</th>
              <th class="num">Вес</th>
              <th>Влияние</th>
              <th>КЕ</th>
            </tr>
          </thead>
          <tbody>
            @for (row of drafts; track row.code) {
              <tr>
                <td class="mono">{{ row.code }}</td>
                <td>{{ row.name }}</td>
                <td class="num">
                  @if (editable) {
                    <input type="number" min="0" max="100" [(ngModel)]="row.weight" [name]="'w-' + row.code" />
                  } @else {
                    {{ row.weight }}
                  }
                </td>
                <td>
                  @if (editable) {
                    <select [(ngModel)]="row.influenceType" [name]="'inf-' + row.code">
                      <option value="weighted">Взвешенный</option>
                      <option value="critical">Критический</option>
                    </select>
                  } @else {
                    {{ influenceLabel(row.influenceType) }}
                  }
                </td>
                <td>
                  @if (editable) {
                    <div class="ci-picks">
                      @for (ci of cis; track ci.id) {
                        <label>
                          <input
                            type="checkbox"
                            [checked]="row.ciIds.includes(ci.id)"
                            (change)="toggleCi(row, ci.id, $any($event.target).checked)"
                          />
                          <span class="mono">{{ ci.fqdn }}</span>
                        </label>
                      }
                    </div>
                  } @else {
                    {{ row.ciIds.length }}
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }
    </div>
  `,
  styles: [
    `
      .panel { background: var(--bg-card); border: 1px solid var(--border); border-radius: 8px; padding: 1rem; }
      .head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem; }
      h3 { margin: 0; font-size: 0.875rem; color: var(--text-primary); font-weight: 500; }
      .empty, .error { margin: 0; font-size: 0.875rem; }
      .error { color: var(--danger); }
      .empty { color: var(--text-muted); }
      table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }
      th, td { padding: 0.5rem 0.375rem; border-bottom: 1px solid var(--border); text-align: left; color: var(--text-primary); }
      th { font-size: 0.75rem; color: var(--text-muted); font-weight: 400; }
      .num { text-align: right; }
      .mono { font-family: var(--font-mono, monospace); font-size: 0.75rem; }
      input[type='number'], select {
        width: 5rem; padding: 0.25rem 0.375rem; background: var(--bg-card); border: 1px solid var(--border);
        border-radius: 8px; color: var(--text-primary);
      }
      select { width: 8.5rem; }
      .ci-picks { display: flex; flex-direction: column; gap: 0.25rem; max-height: 6rem; overflow: auto; }
      .ci-picks label { display: flex; gap: 0.375rem; align-items: center; font-size: 0.75rem; }
      .btn-primary {
        padding: 0.375rem 0.75rem; background: var(--accent); color: #fff;
        border: none; border-radius: 8px; font-size: 0.75rem; cursor: pointer;
      }
      .btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
    `,
  ],
})
export class ComponentWeightEditorComponent {
  @Input() cis: ConfigurationItem[] = [];
  @Input() editable = false;
  @Input() saving = false;
  displayError = '';
  @Output() saveComponents = new EventEmitter<ProductComponentPatch[]>();

  drafts: ComponentDraft[] = [];

  @Input()
  set error(value: string) {
    this.displayError = value;
  }

  @Input()
  set components(value: ProductComponentAdmin[] | undefined) {
    this.drafts = (value ?? []).map((c) => ({
      code: c.code,
      name: c.name,
      weight: c.weight,
      influenceType: c.influenceType,
      criticalThreshold: c.criticalThreshold,
      ciIds: [...c.ciIds],
    }));
  }

  influenceLabel(type: string): string {
    return type === 'critical' ? 'Критический' : 'Взвешенный';
  }

  toggleCi(row: ComponentDraft, ciId: string, checked: boolean): void {
    const set = new Set(row.ciIds);
    if (checked) set.add(ciId);
    else set.delete(ciId);
    row.ciIds = [...set];
  }

  save(): void {
    const total = this.drafts.reduce((sum, d) => sum + Number(d.weight || 0), 0);
    if (total <= 0) {
      this.displayError = 'Сумма весов должна быть больше 0';
      return;
    }
    this.displayError = '';
    this.saveComponents.emit(
      this.drafts.map((d) => ({
        code: d.code,
        name: d.name,
        weight: Number(d.weight),
        influenceType: d.influenceType,
        criticalThreshold: d.criticalThreshold,
        ciIds: d.ciIds,
      })),
    );
  }
}

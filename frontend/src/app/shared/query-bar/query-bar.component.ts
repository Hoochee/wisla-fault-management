import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  QUERY_FIELD_LABELS,
  QueryChip,
  QueryField,
  QueryFilterState,
  QueryOperator,
  SEVERITY_OPTIONS,
  STATUS_OPTIONS,
} from './query-bar.models';

@Component({
  selector: 'app-query-bar',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="query-bar">
      <div class="chips-row">
        <button type="button" class="btn-add" (click)="toggleAdd()" title="Добавить фильтр">+</button>
        @if (showAdd()) {
          <div class="add-form">
            <select [(ngModel)]="draftField" class="sel">
              <option value="severity">severity</option>
              <option value="status">status</option>
              <option value="title">title</option>
            </select>
            <select [(ngModel)]="draftOperator" class="sel narrow">
              <option value="eq">=</option>
              <option value="ne">≠</option>
            </select>
            @if (draftField === 'title') {
              <input type="text" [(ngModel)]="draftValue" class="inp" placeholder="значение" />
            } @else if (draftField === 'severity') {
              <select [(ngModel)]="draftValue" class="sel">
                @for (s of severityOptions; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
            } @else {
              <select [(ngModel)]="draftValue" class="sel">
                @for (s of statusOptions; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
            }
            <button type="button" class="btn-ok" (click)="addChip()">OK</button>
          </div>
        }
        @for (chip of chips(); track chip.id; let i = $index) {
          @if (i > 0) {
            <span class="and">AND</span>
          }
          <div class="chip">
            <span class="field">{{ fieldLabel(chip.field) }}</span>
            <span class="op">{{ chip.operator === 'eq' ? '=' : '≠' }}</span>
            <span class="val">{{ chip.value }}</span>
            <button type="button" class="chip-remove" (click)="removeChip(chip.id)">×</button>
          </div>
        }
      </div>
      <input
        type="text"
        class="search"
        placeholder="Поиск..."
        [ngModel]="textSearch()"
        (ngModelChange)="onTextSearch($event)"
      />
      <input
        type="datetime-local"
        class="dt"
        title="С"
        [ngModel]="fromLocal()"
        (ngModelChange)="onFrom($event)"
      />
      <input
        type="datetime-local"
        class="dt"
        title="По"
        [ngModel]="toLocal()"
        (ngModelChange)="onTo($event)"
      />
      <button type="button" class="btn-refresh" (click)="refresh.emit()" title="Обновить">⟳</button>
    </div>
  `,
  styles: [
    `
      .query-bar {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .chips-row {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex: 1;
        min-width: 200px;
        flex-wrap: wrap;
      }
      .btn-add {
        padding: 0.25rem 0.5rem;
        font-size: 0.75rem;
        border: 1px solid var(--border);
        border-radius: 4px;
        background: transparent;
        color: var(--text-muted);
        cursor: pointer;
      }
      .btn-add:hover {
        color: var(--text-primary);
        border-color: var(--accent);
      }
      .add-form {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-wrap: wrap;
      }
      .sel,
      .inp {
        padding: 0.25rem 0.375rem;
        font-size: 0.75rem;
        border: 1px solid var(--border);
        border-radius: 4px;
        background: var(--bg-sidebar);
        color: var(--text-primary);
      }
      .sel.narrow {
        width: 3rem;
      }
      .btn-ok {
        padding: 0.25rem 0.5rem;
        font-size: 0.75rem;
        border: 1px solid var(--accent);
        border-radius: 4px;
        background: rgba(47, 111, 237, 0.15);
        color: var(--accent);
        cursor: pointer;
      }
      .chip {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem 0.5rem;
        background: var(--bg-sidebar);
        border: 1px solid var(--border);
        border-radius: 4px;
        font-size: 0.75rem;
      }
      .field {
        color: var(--text-muted);
      }
      .op {
        color: var(--text-secondary);
      }
      .val {
        color: var(--accent);
      }
      .chip-remove {
        margin-left: 0.125rem;
        background: none;
        border: none;
        color: var(--text-muted);
        cursor: pointer;
        padding: 0;
        line-height: 1;
      }
      .chip-remove:hover {
        color: var(--text-primary);
      }
      .and {
        font-size: 0.6875rem;
        color: var(--text-muted);
      }
      .search {
        padding: 0.375rem 0.75rem;
        font-size: 0.75rem;
        width: 12rem;
        border: 1px solid var(--border);
        border-radius: 4px;
        background: var(--bg-sidebar);
        color: var(--text-primary);
      }
      .search:focus,
      .dt:focus {
        outline: none;
        border-color: var(--accent);
      }
      .search::placeholder {
        color: var(--text-muted);
      }
      .dt {
        padding: 0.375rem 0.5rem;
        font-size: 0.75rem;
        border: 1px solid var(--border);
        border-radius: 4px;
        background: var(--bg-sidebar);
        color: var(--text-primary);
      }
      .btn-refresh {
        padding: 0.375rem 0.5rem;
        font-size: 0.75rem;
        border: 1px solid var(--border);
        border-radius: 4px;
        background: var(--bg-sidebar);
        color: var(--text-secondary);
        cursor: pointer;
      }
      .btn-refresh:hover {
        background: rgba(58, 63, 75, 0.5);
      }
    `,
  ],
})
export class QueryBarComponent {
  readonly severityOptions = SEVERITY_OPTIONS;
  readonly statusOptions = STATUS_OPTIONS;

  readonly chips = signal<QueryChip[]>([]);
  readonly textSearch = signal('');
  readonly fromIso = signal<string | undefined>(undefined);
  readonly toIso = signal<string | undefined>(undefined);
  readonly showAdd = signal(false);

  draftField: QueryField = 'severity';
  draftOperator: QueryOperator = 'eq';
  draftValue = 'critical';

  @Output() readonly filterChange = new EventEmitter<QueryFilterState>();
  @Output() readonly refresh = new EventEmitter<void>();

  @Input()
  set filter(value: QueryFilterState | undefined) {
    if (!value) return;
    this.chips.set(value.chips);
    this.textSearch.set(value.textSearch);
    this.fromIso.set(value.from);
    this.toIso.set(value.to);
  }

  fieldLabel(field: QueryField): string {
    return QUERY_FIELD_LABELS[field];
  }

  toggleAdd(): void {
    this.showAdd.update((v) => !v);
    this.draftField = 'severity';
    this.draftOperator = 'eq';
    this.draftValue = 'critical';
  }

  addChip(): void {
    const value = this.draftValue.trim();
    if (!value) return;
    this.chips.update((list) => [
      ...list,
      { id: crypto.randomUUID(), field: this.draftField, operator: this.draftOperator, value },
    ]);
    this.showAdd.set(false);
    this.emitChange();
  }

  removeChip(id: string): void {
    this.chips.update((list) => list.filter((c) => c.id !== id));
    this.emitChange();
  }

  onTextSearch(value: string): void {
    this.textSearch.set(value);
    this.emitChange();
  }

  fromLocal(): string {
    return this.isoToLocal(this.fromIso());
  }

  toLocal(): string {
    return this.isoToLocal(this.toIso());
  }

  onFrom(local: string): void {
    this.fromIso.set(local ? new Date(local).toISOString() : undefined);
    this.emitChange();
  }

  onTo(local: string): void {
    this.toIso.set(local ? new Date(local).toISOString() : undefined);
    this.emitChange();
  }

  setTimeRange(from?: string, to?: string): void {
    this.fromIso.set(from);
    this.toIso.set(to);
    this.emitChange();
  }

  private emitChange(): void {
    this.filterChange.emit(this.currentState());
  }

  private currentState(): QueryFilterState {
    return {
      chips: this.chips(),
      textSearch: this.textSearch(),
      from: this.fromIso(),
      to: this.toIso(),
    };
  }

  private isoToLocal(iso?: string): string {
    if (!iso) return '';
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }
}

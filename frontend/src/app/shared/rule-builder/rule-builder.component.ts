import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RuleType } from '../../core/api/api.models';
import { RuleCanvasComponent } from './rule-canvas.component';
import {
  BlockType,
  RuleBlock,
  RuleConnection,
  defaultBlocks,
  defaultConnections,
  palette,
  typeLabels,
  POST_MVP_PALETTE_TYPES,
} from './rule-canvas.models';
import { canvasToEditor } from './rule-canvas-mapper';
import { RuleCanvas } from '../../core/api/api.models';

let nextBlockNum = 100;

@Component({
  selector: 'app-rule-builder',
  standalone: true,
  imports: [FormsModule, RuleCanvasComponent],
  template: `
    <div class="layout">
      <div class="canvas-col">
        <app-rule-canvas
          [blocks]="blocks"
          [connections]="connections"
          [selectedId]="selectedId"
          (selectedIdChange)="selectedId = $event"
          (blockMove)="onMove($event)"
          (connect)="onConnect($event)"
        />
      </div>

      <div class="sidebar">
        @if (showMeta) {
          <section class="panel">
            <h3>Правило</h3>
            <div class="field">
              <label for="rule-name">Название</label>
              <input id="rule-name" type="text" [(ngModel)]="meta.name" class="input" />
            </div>
            <div class="field">
              <label for="rule-type">Тип</label>
              <select id="rule-type" [(ngModel)]="meta.ruleType" class="input">
                <option value="dedup">Дедупликация</option>
                <option value="threshold">Порог</option>
                <option value="problem_resolution">Problem–Resolution</option>
                <option value="correlation">Корреляция</option>
              </select>
            </div>
            <div class="field">
              <label for="rule-desc">Описание</label>
              <textarea id="rule-desc" [(ngModel)]="meta.description" rows="2" class="input textarea"></textarea>
            </div>
          </section>
        }

        <section class="panel">
          <h3>Палитра блоков</h3>
          <p class="hint">Нажмите, чтобы добавить на холст</p>
          @for (item of palette; track item.label + item.type) {
            <button
              type="button"
              class="palette-btn"
              [class.palette-btn--post-mvp]="isPostMvpPaletteItem(item)"
              [title]="paletteTooltip(item)"
              (click)="addBlock(item)"
            >
              + {{ item.label }}
              @if (isPostMvpPaletteItem(item)) {
                <span class="post-mvp-tag">post-MVP</span>
              }
            </button>
          }
        </section>

        <section class="panel">
          <h3>Свойства блока</h3>
          @if (!selected) {
            <p class="hint">Выберите блок на холсте или добавьте из палитры</p>
          } @else {
            <div class="field">
              <span class="label-muted">Тип блока</span>
              <div class="value">{{ typeLabels[selected.type] }}</div>
            </div>
            <div class="field">
              <label for="block-label">Подпись на холсте</label>
              <input
                id="block-label"
                type="text"
                [ngModel]="selected.label"
                (ngModelChange)="updateBlock(selected.id, { label: $event })"
                class="input"
              />
            </div>

            @if (selected.type === 'trigger') {
              <div class="field">
                <label for="trigger-type">Триггер</label>
                <select
                  id="trigger-type"
                  [ngModel]="selected.config['triggerType'] ?? 'stream'"
                  (ngModelChange)="onTriggerTypeChange(selected.id, $event)"
                  class="input"
                >
                  <option value="stream">Событие потока</option>
                  <option value="fm">Событие FM</option>
                </select>
              </div>
            }

            @if (selected.type === 'condition') {
              <div class="field">
                <label for="cond-field">Поле</label>
                <select
                  id="cond-field"
                  [ngModel]="selected.config['field'] ?? 'severity'"
                  (ngModelChange)="updateConfig(selected.id, 'field', $event)"
                  class="input"
                >
                  <option value="severity">severity</option>
                  <option value="source_id">source_id</option>
                  <option value="ci_id">ci_id</option>
                  <option value="title">title</option>
                  <option value="status">status</option>
                </select>
              </div>
              <div class="field">
                <label for="cond-op">Оператор</label>
                <select
                  id="cond-op"
                  [ngModel]="selected.config['operator'] ?? 'eq'"
                  (ngModelChange)="updateConfig(selected.id, 'operator', $event)"
                  class="input"
                >
                  <option value="eq">Равно</option>
                  <option value="contains">Содержит</option>
                  <option value="gt">Больше</option>
                  <option value="in">В списке</option>
                </select>
              </div>
              <div class="field">
                <label for="cond-val">Значение</label>
                <input
                  id="cond-val"
                  type="text"
                  [ngModel]="selected.config['value'] ?? ''"
                  (ngModelChange)="onConditionValueChange(selected.id, $event)"
                  class="input mono"
                />
              </div>
            }

            @if (selected.type === 'dedup') {
              <div class="field">
                <label for="dedup-key">Ключ дедупликации</label>
                <input
                  id="dedup-key"
                  type="text"
                  [ngModel]="selected.config['key'] ?? ''"
                  (ngModelChange)="updateConfig(selected.id, 'key', $event)"
                  class="input mono"
                />
              </div>
            }

            @if (selected.type === 'threshold') {
              <div class="field">
                <label for="thresh-count">Количество событий</label>
                <input
                  id="thresh-count"
                  type="number"
                  min="1"
                  [ngModel]="selected.config['count'] ?? '5'"
                  (ngModelChange)="onThresholdCountChange(selected.id, $event)"
                  class="input"
                />
              </div>
              <div class="field">
                <label for="thresh-window">Окно (мин)</label>
                <input
                  id="thresh-window"
                  type="number"
                  min="1"
                  [ngModel]="selected.config['windowMin'] ?? '10'"
                  (ngModelChange)="onThresholdWindowChange(selected.id, $event)"
                  class="input"
                />
              </div>
            }

            @if (selected.type === 'correlation') {
              <div class="field">
                <label for="corr-count">Количество событий</label>
                <input
                  id="corr-count"
                  type="number"
                  min="2"
                  [ngModel]="selected.config['count'] ?? '2'"
                  (ngModelChange)="onCorrelationCountChange(selected.id, $event)"
                  class="input"
                />
              </div>
              <div class="field">
                <label for="corr-window">Окно (мин)</label>
                <input
                  id="corr-window"
                  type="number"
                  min="1"
                  [ngModel]="selected.config['windowMin'] ?? '15'"
                  (ngModelChange)="onCorrelationWindowChange(selected.id, $event)"
                  class="input"
                />
              </div>
              <div class="field">
                <label for="corr-match">Поле группировки (опционально)</label>
                <select
                  id="corr-match"
                  [ngModel]="selected.config['matchField'] ?? ''"
                  (ngModelChange)="onCorrelationMatchFieldChange(selected.id, $event)"
                  class="input"
                >
                  <option value="">— любое —</option>
                  <option value="title">title</option>
                  <option value="severity">severity</option>
                  <option value="source">source</option>
                </select>
              </div>
              <p class="hint">
                Первое событие в окне — root, последующие — children (root_event_id в консоли).
              </p>
            }

            @if (selected.type === 'notify') {
              <div class="field">
                <label for="notify-channel">Канал</label>
                <select
                  id="notify-channel"
                  [ngModel]="selected.config['channel'] ?? 'email'"
                  (ngModelChange)="onNotifyChannelChange(selected.id, $event)"
                  class="input"
                >
                  <option value="email">Email</option>
                  <option value="telegram">Telegram</option>
                </select>
              </div>
              @if ((selected.config['channel'] ?? 'email') === 'email') {
                <div class="field">
                  <label for="notify-email">Email адрес</label>
                  <input
                    id="notify-email"
                    type="email"
                    [ngModel]="selected.config['emailAddress'] ?? ''"
                    (ngModelChange)="onNotifyEmailChange(selected.id, $event)"
                    class="input mono"
                    placeholder="operator@example.com"
                  />
                </div>
              }
              @if ((selected.config['channel'] ?? 'email') === 'telegram') {
                <p class="post-mvp-warn" title="Post-MVP: реальная отправка не выполняется">
                  Post-MVP: Telegram-бот не подключён — блок фиксирует намерение, без реальной отправки.
                </p>
              }
              <p class="hint">
                MVP+: заглушка — намерение сохраняется в правиле, доставка не выполняется.
              </p>
            }

            @if (selected.type === 'push') {
              <div class="field">
                <label for="push-message">Шаблон сообщения (опционально)</label>
                <textarea
                  id="push-message"
                  rows="3"
                  [ngModel]="selected.config['message'] ?? ''"
                  (ngModelChange)="onPushMessageChange(selected.id, $event)"
                  class="input textarea mono"
                  placeholder="Critical: {title} ({severity})"
                ></textarea>
              </div>
              <p class="hint">
                Плейсхолдеры: <code>&#123;title&#125;</code>, <code>&#123;severity&#125;</code>. Пустое значение — заголовок события.
              </p>
            }

            @if (selected.type === 'status') {
              <p class="post-mvp-warn" title="Не исполняется в runtime">
                Post-MVP: блок сохраняется в схеме, но не выполняется при обработке событий.
              </p>
              <div class="field">
                <label for="status-val">Новый статус</label>
                <select
                  id="status-val"
                  [ngModel]="selected.config['status'] ?? 'in_progress'"
                  (ngModelChange)="onStatusChange(selected.id, $event)"
                  class="input"
                  disabled
                  title="Не исполняется в runtime (post-MVP)"
                >
                  <option value="in_progress">В работе</option>
                  <option value="closed">Закрыт</option>
                  <option value="deferred">Отложен</option>
                  <option value="maintenance">Обслуживание</option>
                </select>
              </div>
            }

            @if (selected.type === 'switch') {
              <p class="hint">
                Исходящие ветки проверяются по порядку связей. Выполняется
                <strong>первая подходящая</strong> ветка (с condition на пути); остальные пропускаются.
              </p>
            }

            <div class="links">
              <h4>Связи</h4>
              <div class="link-group">
                <span class="link-label">Исходящие →</span>
                @if (outgoing.length === 0) {
                  <p class="hint">Нет исходящих связей</p>
                } @else {
                  @for (c of outgoing; track c.from + c.to) {
                    <div class="link-row">
                      <span>{{ labelFor(c.to) }}</span>
                      <button type="button" class="unlink" (click)="disconnect(c.from, c.to)">×</button>
                    </div>
                  }
                }
              </div>
              <div class="link-group">
                <span class="link-label">← Входящие</span>
                @if (incoming.length === 0) {
                  <p class="hint">Нет входящих связей</p>
                } @else {
                  @for (c of incoming; track c.from + c.to) {
                    <div class="link-row">
                      <span>{{ labelFor(c.from) }}</span>
                      <button type="button" class="unlink" (click)="disconnect(c.from, c.to)">×</button>
                    </div>
                  }
                }
              </div>
              <div class="link-add">
                <select #linkTarget class="input">
                  <option value="" disabled selected>К блоку…</option>
                  @for (b of otherBlocks; track b.id) {
                    <option [value]="b.id">{{ b.label }}</option>
                  }
                </select>
                <button type="button" class="btn-link" (click)="linkToSelected(linkTarget)">Связать →</button>
              </div>
            </div>

            @if (selected.type !== 'trigger') {
              <button type="button" class="btn-delete" (click)="deleteBlock(selected.id)">Удалить блок</button>
            }
          }
        </section>
      </div>
    </div>
  `,
  styles: [
    `
      .layout {
        display: grid;
        grid-template-columns: 1fr;
        gap: 1rem;
      }
      @media (min-width: 1024px) {
        .layout {
          grid-template-columns: 2fr 1fr;
        }
      }
      .sidebar {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }
      .panel {
        background: var(--bg-card);
        border: 1px solid var(--border);
        border-radius: 8px;
        padding: 1rem;
      }
      .panel h3 {
        margin: 0 0 0.75rem;
        font-size: 0.875rem;
        color: var(--text-primary);
      }
      .panel h4 {
        margin: 0 0 0.5rem;
        font-size: 0.75rem;
        color: var(--text-primary);
      }
      .field {
        margin-bottom: 0.75rem;
      }
      .field label,
      .label-muted {
        display: block;
        font-size: 0.6875rem;
        color: var(--text-muted);
        margin-bottom: 0.25rem;
      }
      .value {
        font-size: 0.75rem;
        color: var(--text-primary);
      }
      .input {
        width: 100%;
        padding: 0.375rem 0.5rem;
        background: var(--bg-primary);
        border: 1px solid var(--border);
        border-radius: 4px;
        color: var(--text-primary);
        font-size: 0.75rem;
        box-sizing: border-box;
      }
      .textarea {
        resize: none;
      }
      .mono {
        font-family: var(--font-mono);
      }
      .hint {
        font-size: 0.6875rem;
        color: var(--text-muted);
        margin: 0 0 0.5rem;
      }
      .hint code {
        font-family: var(--font-mono);
        color: var(--text-secondary);
      }
      .palette-btn {
        display: block;
        width: 100%;
        text-align: left;
        padding: 0.375rem 0.5rem;
        margin-bottom: 0.25rem;
        background: var(--bg-primary);
        border: 1px solid var(--border);
        border-radius: 4px;
        color: var(--text-secondary);
        font-size: 0.75rem;
        cursor: pointer;
      }
      .palette-btn:hover {
        border-color: rgba(47, 111, 237, 0.5);
        color: var(--text-primary);
      }
      .palette-btn--post-mvp {
        border-color: rgba(245, 158, 11, 0.35);
        color: var(--text-secondary);
      }
      .post-mvp-tag {
        margin-left: 0.375rem;
        font-size: 0.5625rem;
        text-transform: uppercase;
        color: #f59e0b;
        vertical-align: middle;
      }
      .post-mvp-warn {
        font-size: 0.6875rem;
        color: #f59e0b;
        margin: 0 0 0.75rem;
        padding: 0.5rem;
        background: rgba(245, 158, 11, 0.08);
        border: 1px solid rgba(245, 158, 11, 0.25);
        border-radius: 4px;
      }
      .links {
        padding-top: 0.5rem;
        border-top: 1px solid var(--border);
      }
      .link-group {
        margin-bottom: 0.5rem;
      }
      .link-label {
        font-size: 0.625rem;
        text-transform: uppercase;
        color: var(--text-muted);
      }
      .link-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.75rem;
        color: var(--text-primary);
        margin-top: 0.25rem;
      }
      .unlink {
        background: none;
        border: none;
        color: #f87171;
        cursor: pointer;
        font-size: 1rem;
        line-height: 1;
      }
      .link-add {
        display: flex;
        gap: 0.25rem;
        margin-top: 0.25rem;
      }
      .btn-link {
        padding: 0.375rem 0.5rem;
        background: var(--accent);
        color: #fff;
        border: none;
        border-radius: 4px;
        font-size: 0.75rem;
        cursor: pointer;
        flex-shrink: 0;
      }
      .btn-delete {
        width: 100%;
        margin-top: 0.5rem;
        padding: 0.375rem 0.75rem;
        font-size: 0.75rem;
        color: #f87171;
        background: transparent;
        border: 1px solid rgba(239, 68, 68, 0.3);
        border-radius: 4px;
        cursor: pointer;
      }
      .btn-delete:hover {
        background: rgba(239, 68, 68, 0.1);
      }
    `,
  ],
})
export class RuleBuilderComponent implements OnChanges {
  @Input() showMeta = true;
  @Input() initialCanvas?: RuleCanvas | null;
  @Input() initialMeta?: { name?: string; description?: string; ruleType?: RuleType };

  readonly palette = palette;
  readonly typeLabels = typeLabels;

  blocks: RuleBlock[] = [...defaultBlocks];
  connections: RuleConnection[] = [...defaultConnections];
  selectedId: string | null = null;

  meta = {
    name: '',
    description: '',
    ruleType: 'dedup' as RuleType,
  };

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['initialCanvas'] && this.initialCanvas) {
      const editor = canvasToEditor(this.initialCanvas);
      this.blocks = editor.blocks;
      this.connections = editor.connections;
    }
    if (changes['initialMeta'] && this.initialMeta) {
      this.meta = {
        name: this.initialMeta.name ?? '',
        description: this.initialMeta.description ?? '',
        ruleType: this.initialMeta.ruleType ?? 'dedup',
      };
    }
  }

  get selected(): RuleBlock | null {
    return this.blocks.find((b) => b.id === this.selectedId) ?? null;
  }

  get outgoing(): RuleConnection[] {
    return this.connections.filter((c) => c.from === this.selectedId);
  }

  get incoming(): RuleConnection[] {
    return this.connections.filter((c) => c.to === this.selectedId);
  }

  get otherBlocks(): RuleBlock[] {
    return this.blocks.filter((b) => b.id !== this.selectedId);
  }

  getBlocks(): RuleBlock[] {
    return this.blocks;
  }

  getConnections(): RuleConnection[] {
    return this.connections;
  }

  getMeta(): { name: string; description: string; ruleType: RuleType } {
    return { ...this.meta };
  }

  labelFor(id: string): string {
    return this.blocks.find((b) => b.id === id)?.label ?? id;
  }

  onMove(event: { id: string; x: number; y: number }): void {
    this.blocks = this.blocks.map((b) => (b.id === event.id ? { ...b, x: event.x, y: event.y } : b));
  }

  onConnect(event: { from: string; to: string }): void {
    if (this.connections.some((c) => c.from === event.from && c.to === event.to)) return;
    this.connections = [...this.connections, event];
  }

  disconnect(from: string, to: string): void {
    this.connections = this.connections.filter((c) => !(c.from === from && c.to === to));
  }

  addBlock(item: (typeof palette)[number]): void {
    const id = `b${nextBlockNum++}`;
    const offset = this.blocks.length * 24;
    const newBlock: RuleBlock = {
      id,
      type: item.type,
      label: item.defaultLabel,
      x: 40 + (offset % 200),
      y: 40 + (offset % 300),
      config: { ...item.defaultConfig },
    };
    this.blocks = [...this.blocks, newBlock];
    this.selectedId = id;
  }

  updateBlock(id: string, patch: Partial<RuleBlock>): void {
    this.blocks = this.blocks.map((b) => (b.id === id ? { ...b, ...patch } : b));
  }

  updateConfig(id: string, key: string, value: string): void {
    this.blocks = this.blocks.map((b) =>
      b.id === id ? { ...b, config: { ...b.config, [key]: value } } : b,
    );
  }

  onTriggerTypeChange(id: string, triggerType: string): void {
    this.updateConfig(id, 'triggerType', triggerType);
    this.updateBlock(id, { label: triggerType === 'fm' ? 'Событие FM' : 'Событие потока' });
  }

  onConditionValueChange(id: string, value: string): void {
    const block = this.blocks.find((b) => b.id === id);
    if (!block) return;
    const field = block.config['field'] ?? 'severity';
    const op = block.config['operator'] === 'eq' ? '=' : (block.config['operator'] ?? '=');
    this.updateConfig(id, 'value', value);
    this.updateBlock(id, { label: `${field} ${op} ${value}` });
  }

  onThresholdCountChange(id: string, count: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const windowMin = block?.config['windowMin'] ?? '10';
    this.updateConfig(id, 'count', count);
    this.updateBlock(id, { label: `Порог: ${count} за ${windowMin} мин` });
  }

  onThresholdWindowChange(id: string, windowMin: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const count = block?.config['count'] ?? '5';
    this.updateConfig(id, 'windowMin', windowMin);
    this.updateBlock(id, { label: `Порог: ${count} за ${windowMin} мин` });
  }

  onCorrelationCountChange(id: string, count: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const windowMin = block?.config['windowMin'] ?? '15';
    const matchField = block?.config['matchField'] ?? '';
    this.updateConfig(id, 'count', count);
    this.updateBlock(id, { label: this.correlationLabel(count, windowMin, matchField) });
  }

  onCorrelationWindowChange(id: string, windowMin: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const count = block?.config['count'] ?? '2';
    const matchField = block?.config['matchField'] ?? '';
    this.updateConfig(id, 'windowMin', windowMin);
    this.updateBlock(id, { label: this.correlationLabel(count, windowMin, matchField) });
  }

  onCorrelationMatchFieldChange(id: string, matchField: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const count = block?.config['count'] ?? '2';
    const windowMin = block?.config['windowMin'] ?? '15';
    this.updateConfig(id, 'matchField', matchField);
    this.updateBlock(id, { label: this.correlationLabel(count, windowMin, matchField) });
  }

  private correlationLabel(count: string, windowMin: string, matchField: string): string {
    const base = `Корреляция: ${count} за ${windowMin} мин`;
    return matchField ? `${base} · ${matchField}` : base;
  }

  onNotifyChannelChange(id: string, channel: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const emailAddress = block?.config['emailAddress'] ?? '';
    this.updateConfig(id, 'channel', channel);
    this.updateBlock(id, { label: this.notifyLabel(channel, emailAddress) });
  }

  onNotifyEmailChange(id: string, emailAddress: string): void {
    const block = this.blocks.find((b) => b.id === id);
    const channel = block?.config['channel'] ?? 'email';
    this.updateConfig(id, 'emailAddress', emailAddress);
    this.updateBlock(id, { label: this.notifyLabel(channel, emailAddress) });
  }

  private notifyLabel(channel: string, emailAddress: string): string {
    if (channel === 'telegram') {
      return 'Notify: Telegram';
    }
    return emailAddress ? `Notify: ${emailAddress}` : 'Notify: email';
  }

  onPushMessageChange(id: string, message: string): void {
    this.updateConfig(id, 'message', message);
    const trimmed = message.trim();
    this.updateBlock(id, { label: trimmed ? `Push: ${this.truncate(trimmed, 24)}` : 'Push' });
  }

  private truncate(text: string, max: number): string {
    return text.length <= max ? text : `${text.slice(0, max - 1)}…`;
  }

  isPostMvpPaletteItem(item: (typeof palette)[number]): boolean {
    return POST_MVP_PALETTE_TYPES.has(item.type);
  }

  paletteTooltip(item: (typeof palette)[number]): string | null {
    if (item.type === 'status') {
      return 'Post-MVP: не исполняется в runtime';
    }
    return null;
  }

  onStatusChange(id: string, status: string): void {
    const labels: Record<string, string> = {
      in_progress: 'В работе',
      closed: 'Закрыт',
      deferred: 'Отложен',
      maintenance: 'Обслуживание',
    };
    this.updateConfig(id, 'status', status);
    this.updateBlock(id, { label: `Статус → ${labels[status] ?? status}` });
  }

  deleteBlock(id: string): void {
    this.blocks = this.blocks.filter((b) => b.id !== id);
    this.connections = this.connections.filter((c) => c.from !== id && c.to !== id);
    this.selectedId = null;
  }

  linkToSelected(select: HTMLSelectElement): void {
    const to = select.value;
    if (!to || !this.selectedId) return;
    this.onConnect({ from: this.selectedId, to });
    select.value = '';
  }
}

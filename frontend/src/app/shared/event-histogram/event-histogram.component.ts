import { Component, EventEmitter, Input, Output, computed, signal } from '@angular/core';

export interface HistogramBucket {
  start: Date;
  end: Date;
  count: number;
  label: string;
}

export interface TimeRangeSelection {
  from: string;
  to: string;
}

@Component({
  selector: 'app-event-histogram',
  standalone: true,
  template: `
  <div class="histogram" role="img" aria-label="Гистограмма событий по времени">
    @if (buckets().length === 0) {
      <span class="empty">Нет данных для гистограммы</span>
    } @else {
      @for (b of buckets(); track b.label) {
        <button
          type="button"
          class="bar"
          [class.selected]="selectedIndex() === $index"
          [style.height.%]="barHeight(b.count)"
          [title]="b.count + ' событий · ' + b.label"
          (click)="selectBucket($index)"
        ></button>
      }
    }
  </div>
  `,
  styles: [
    `
      .histogram {
        height: 4rem;
        display: flex;
        align-items: flex-end;
        gap: 1px;
        padding: 0.25rem 0.5rem;
        background: var(--bg-sidebar);
        border: 1px solid var(--border);
        border-radius: 6px;
        margin-bottom: 0.75rem;
      }
      .empty {
        margin: auto;
        font-size: 0.75rem;
        color: var(--text-muted);
      }
      .bar {
        flex: 1;
        min-width: 0;
        border: none;
        padding: 0;
        background: rgba(47, 111, 237, 0.6);
        border-radius: 2px 2px 0 0;
        cursor: pointer;
        transition: background 0.15s;
      }
      .bar:hover,
      .bar.selected {
        background: var(--accent);
      }
    `,
  ],
})
export class EventHistogramComponent {
  private readonly timestamps = signal<string[]>([]);
  readonly selectedIndex = signal<number | null>(null);

  @Output() readonly timeRangeSelect = new EventEmitter<TimeRangeSelection | null>();

  @Input()
  set eventTimestamps(values: string[]) {
    this.timestamps.set(values);
    this.selectedIndex.set(null);
  }

  readonly buckets = computed(() => this.computeBuckets(this.timestamps()));

  readonly maxCount = computed(() => {
    const counts = this.buckets().map((b) => b.count);
    return counts.length ? Math.max(...counts) : 1;
  });

  barHeight(count: number): number {
    const max = this.maxCount();
    if (!max) return 0;
    return Math.max(4, (count / max) * 100);
  }

  selectBucket(index: number): void {
    if (this.selectedIndex() === index) {
      this.selectedIndex.set(null);
      this.timeRangeSelect.emit(null);
      return;
    }
    this.selectedIndex.set(index);
    const bucket = this.buckets()[index];
    this.timeRangeSelect.emit({
      from: bucket.start.toISOString(),
      to: bucket.end.toISOString(),
    });
  }

  private computeBuckets(timestamps: string[]): HistogramBucket[] {
    if (!timestamps.length) return [];

    const dates = timestamps.map((t) => new Date(t).getTime()).filter((t) => !Number.isNaN(t));
    if (!dates.length) return [];

    const now = Date.now();
    const latest = Math.max(...dates, now);
    const bucketMs = 60 * 60 * 1000;
    const rangeStart = latest - 23 * bucketMs;
    const buckets: HistogramBucket[] = [];

    for (let i = 0; i < 24; i++) {
      const start = new Date(rangeStart + i * bucketMs);
      const end = new Date(rangeStart + (i + 1) * bucketMs - 1);
      const count = dates.filter((t) => t >= start.getTime() && t < end.getTime() + 1).length;
      const label = start.toLocaleString('ru-RU', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' });
      buckets.push({ start, end, count, label });
    }
    return buckets;
  }
}

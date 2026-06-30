import { Component, inject, OnInit, signal, ViewChild } from '@angular/core';

import { DatePipe } from '@angular/common';

import { FmApiService } from '../../core/api/fm-api.service';

import { RawEvent } from '../../core/api/api.models';

import { PageHeaderComponent } from '../../shared/page-header/page-header.component';

import { SeverityBadgeComponent } from '../../shared/severity-badge/severity-badge.component';

import { QueryBarComponent } from '../../shared/query-bar/query-bar.component';

import { QueryFilterState } from '../../shared/query-bar/query-bar.models';

import {

  applyClientRawEventFilter,

  buildRawEventApiParams,

} from '../../shared/query-bar/query-filter.util';

import {

  EventHistogramComponent,

  TimeRangeSelection,

} from '../../shared/event-histogram/event-histogram.component';



@Component({

  selector: 'app-raw-events',

  standalone: true,

  imports: [

    DatePipe,

    PageHeaderComponent,

    SeverityBadgeComponent,

    QueryBarComponent,

    EventHistogramComponent,

  ],

  template: `

    <app-page-header title="Первичные события" subtitle="Сырые события до дедупликации" />

    <app-query-bar

      #queryBar

      [filter]="filter()"

      (filterChange)="onFilterChange($event)"

      (refresh)="loadEvents()"

    />

    <app-event-histogram

      [eventTimestamps]="histogramTimestamps()"

      (timeRangeSelect)="onHistogramRange($event)"

    />

    <table class="data-table">

      <thead>

        <tr><th>Критичность</th><th>Заголовок</th><th>Источник</th><th>Узел</th><th>Обработано</th><th>Время</th></tr>

      </thead>

      <tbody>

        @for (e of displayedEvents(); track e.id) {

          <tr>

            <td><app-severity-badge [severity]="e.severity" /></td>

            <td>{{ e.title }}</td>

            <td>{{ e.sourceName }}</td>

            <td class="mono">{{ e.nodeFqdn }}</td>

            <td>{{ e.processed ? 'Да' : 'Нет' }}</td>

            <td class="mono">{{ e.createdAt | date:'short' }}</td>

          </tr>

        }

      </tbody>

    </table>

  `,

  styles: [

    `

      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }

      .data-table th, .data-table td { padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border); text-align: left; }

      .data-table th { color: var(--text-muted); }

      .mono { font-family: var(--font-mono); font-size: 0.75rem; }

    `,

  ],

})

export class RawEventsPageComponent implements OnInit {

  private readonly api = inject(FmApiService);



  @ViewChild('queryBar') queryBar?: QueryBarComponent;



  readonly filter = signal<QueryFilterState>({ chips: [], textSearch: '' });

  readonly displayedEvents = signal<RawEvent[]>([]);

  readonly histogramTimestamps = signal<string[]>([]);



  ngOnInit(): void {

    this.loadEvents();

  }



  onFilterChange(state: QueryFilterState): void {

    this.filter.set(state);

    this.loadEvents();

  }



  onHistogramRange(range: TimeRangeSelection | null): void {

    if (!range) {

      this.filter.update((f) => ({ ...f, from: undefined, to: undefined }));

    } else {

      this.filter.update((f) => ({ ...f, from: range.from, to: range.to }));

      this.queryBar?.setTimeRange(range.from, range.to);

    }

    this.loadEvents();

  }



  loadEvents(): void {

    this.api.listRawEvents(buildRawEventApiParams(this.filter())).subscribe((page) => {

      const filtered = applyClientRawEventFilter(page.items, this.filter());

      this.displayedEvents.set(filtered);

      this.histogramTimestamps.set(page.items.map((e) => e.occurredAt ?? e.createdAt));

    });

  }

}



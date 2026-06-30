import { Component, inject, OnDestroy, OnInit, signal, ViewChild } from '@angular/core';

import { RouterLink } from '@angular/router';

import { DatePipe } from '@angular/common';

import { interval, startWith, Subscription, switchMap } from 'rxjs';

import { FmApiService } from '../../core/api/fm-api.service';

import { Event, EventMap } from '../../core/api/api.models';

import { mvpDefaults } from '../../core/config/mvp-defaults';

import { SeverityBadgeComponent } from '../../shared/severity-badge/severity-badge.component';

import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

import { QueryBarComponent } from '../../shared/query-bar/query-bar.component';

import { QueryFilterState } from '../../shared/query-bar/query-bar.models';

import {

  applyClientEventFilter,

  buildEventApiParams,

  EventSortField,

  EventSortState,

} from '../../shared/query-bar/query-filter.util';

import {

  EventHistogramComponent,

  TimeRangeSelection,

} from '../../shared/event-histogram/event-histogram.component';



@Component({

  selector: 'app-console',

  standalone: true,

  imports: [

    RouterLink,

    DatePipe,

    SeverityBadgeComponent,

    StatusBadgeComponent,

    QueryBarComponent,

    EventHistogramComponent,

  ],

  template: `

    <div class="console-layout">

      <aside class="maps-sidebar">

        <div class="maps-title">Карты событий</div>

        @for (map of maps; track map.id) {

          <button type="button" class="map-btn" [class.active]="selectedMapId() === map.id" (click)="selectMap(map.id)">

            {{ map.name }}

          </button>

        }

        <button type="button" class="map-btn add">+ Карта</button>

      </aside>

      <div class="console-main">

        <div class="console-header">

          <div>

            <h1>Консоль событий</h1>

            @if (pollingEnabled) {

              <p class="poll-hint">Обработанные события FM · polling {{ pollSec }} с</p>

            }

          </div>

        </div>

        <app-query-bar

          #queryBar

          [filter]="filter()"

          (filterChange)="onFilterChange($event)"

          (refresh)="refresh()"

        />

        <app-event-histogram

          [eventTimestamps]="histogramTimestamps()"

          (timeRangeSelect)="onHistogramRange($event)"

        />

        <table class="data-table">

          <thead>

            <tr>
              @for (col of sortColumns; track col.field) {
                <th
                  class="sortable"
                  [class.sorted]="sortState().field === col.field"
                  (click)="onSortColumn(col.field)"
                >
                  {{ col.label }}
                  @if (sortState().field === col.field) {
                    <span class="sort-indicator">{{ sortState().direction === 'asc' ? '▲' : '▼' }}</span>
                  }
                </th>
              }
            </tr>

          </thead>

          <tbody>

            @for (e of displayedEvents(); track e.id) {

              <tr [class.selected]="selectedEvent()?.id === e.id" (click)="selectEvent(e)" (dblclick)="openEvent(e)">

                <td><app-status-badge [status]="e.status" /></td>

                <td><app-severity-badge [severity]="e.severity" /></td>

                <td>{{ e.title }}</td>

                <td class="mono">{{ e.nodeFqdn }}</td>

                <td>{{ e.systemName }}</td>

                <td class="mono">{{ e.repeatCount }}</td>

                <td class="mono">
                  @if (e.repeatCount > 1 && e.lastRepeatAt) {
                    {{ e.lastRepeatAt | date:'short' }}
                  } @else {
                    —
                  }
                </td>

                <td class="mono">{{ e.createdAt | date:'short' }}</td>

              </tr>

            }

          </tbody>

        </table>

        @if (selectedEvent(); as ev) {

          <div class="detail-panel">

            <div class="detail-header">

              <app-severity-badge [severity]="ev.severity" />

              <strong>{{ ev.title }}</strong>

              <a [routerLink]="['/console', ev.id]" class="open-full">Открыть полностью →</a>

            </div>

            <p>{{ ev.description }}</p>

          </div>

        }

      </div>

    </div>

  `,

  styles: [

    `

      .console-layout { display: flex; gap: 0; min-height: calc(100vh - 8rem); margin: -1.5rem; }

      .maps-sidebar { width: 200px; background: var(--bg-sidebar); border-right: 1px solid var(--border); padding: 0.75rem; flex-shrink: 0; }

      .maps-title { font-size: 0.6875rem; text-transform: uppercase; color: var(--text-muted); margin-bottom: 0.5rem; }

      .map-btn { display: block; width: 100%; text-align: left; padding: 0.5rem; background: none; border: none; color: var(--text-secondary); font-size: 0.8125rem; border-radius: 4px; cursor: pointer; }

      .map-btn:hover, .map-btn.active { background: rgba(74,158,255,0.12); color: var(--accent); }

      .map-btn.add { color: var(--accent); margin-top: 0.5rem; }

      .console-main { flex: 1; padding: 1rem; overflow: auto; display: flex; flex-direction: column; }

      .console-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.75rem; }

      .console-header h1 { margin: 0; font-size: 1.125rem; color: #fff; }

      .poll-hint { margin: 0.25rem 0 0; font-size: 0.75rem; color: var(--text-muted); }

      .data-table { width: 100%; border-collapse: collapse; font-size: 0.8125rem; }

      .data-table th, .data-table td { padding: 0.375rem 0.5rem; border-bottom: 1px solid var(--border); text-align: left; }

      .data-table th.sortable { cursor: pointer; user-select: none; white-space: nowrap; }

      .data-table th.sortable:hover { color: var(--accent); }

      .data-table th.sorted { color: var(--accent); }

      .sort-indicator { margin-left: 0.25rem; font-size: 0.625rem; }

      .data-table tr { cursor: pointer; }

      .data-table tr:hover { background: rgba(74,158,255,0.05); }

      .data-table tr.selected { background: rgba(74,158,255,0.1); }

      .mono { font-family: var(--font-mono); font-size: 0.6875rem; }

      .detail-panel { margin-top: auto; border-top: 1px solid var(--border); padding-top: 0.75rem; background: var(--bg-sidebar); border-radius: 6px 6px 0 0; padding: 1rem; }

      .detail-header { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 0.5rem; }

      .open-full { margin-left: auto; color: var(--accent); font-size: 0.8125rem; text-decoration: none; }

      p { margin: 0; color: var(--text-secondary); font-size: 0.8125rem; }

    `,

  ],

})

export class ConsolePageComponent implements OnInit, OnDestroy {

  private readonly api = inject(FmApiService);

  private pollSub?: Subscription;



  @ViewChild('queryBar') queryBar?: QueryBarComponent;



  readonly pollingEnabled = mvpDefaults.liveUpdate === 'polling-mvp';

  readonly pollSec = mvpDefaults.consolePollingSec;



  maps: EventMap[] = [];

  private allEvents: Event[] = [];

  readonly filter = signal<QueryFilterState>({ chips: [], textSearch: '' });

  readonly displayedEvents = signal<Event[]>([]);

  readonly histogramTimestamps = signal<string[]>([]);

  readonly selectedMapId = signal<string | null>(null);

  readonly selectedEvent = signal<Event | null>(null);

  readonly sortState = signal<EventSortState>({ field: 'createdAt', direction: 'desc' });

  readonly sortColumns: { field: EventSortField; label: string }[] = [
    { field: 'status', label: 'Статус' },
    { field: 'severity', label: 'Критичность' },
    { field: 'title', label: 'Заголовок' },
    { field: 'nodeFqdn', label: 'Узел' },
    { field: 'systemName', label: 'Система' },
    { field: 'repeatCount', label: 'Повторы' },
    { field: 'lastRepeatAt', label: 'Последний повтор' },
    { field: 'createdAt', label: 'Создано' },
  ];



  ngOnInit(): void {

    this.api.listEventMaps().subscribe((m) => {

      this.maps = m;

      if (m.length) this.selectedMapId.set(m[0].id);

    });



    if (this.pollingEnabled) {

      this.pollSub = interval(this.pollSec * 1000)

        .pipe(

          startWith(0),

          switchMap(() => this.api.listEvents(this.eventParams())),

        )

        .subscribe((page) => this.applyEvents(page.items));

    } else {

      this.loadEvents();

    }

  }



  ngOnDestroy(): void {

    this.pollSub?.unsubscribe();

  }



  selectMap(id: string): void {

    this.selectedMapId.set(id);

    this.loadEvents();

  }



  selectEvent(e: Event): void {

    this.selectedEvent.set(e);

  }



  openEvent(e: Event): void {

    window.open(`/console/${e.id}`, '_blank');

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



  refresh(): void {

    this.loadEvents();

  }



  onSortColumn(field: EventSortField): void {

    this.sortState.update((current) =>

      current.field === field

        ? { field, direction: current.direction === 'asc' ? 'desc' : 'asc' }

        : { field, direction: 'desc' },

    );

    this.loadEvents();

  }



  private loadEvents(): void {

    this.api.listEvents(this.eventParams()).subscribe((page) => this.applyEvents(page.items));

  }



  private applyEvents(items: Event[]): void {

    this.allEvents = items;

    const filtered = applyClientEventFilter(items, this.filter());

    this.displayedEvents.set(filtered);

    this.histogramTimestamps.set(items.map((e) => e.createdAt));

  }



  private eventParams(): Record<string, string> {

    return buildEventApiParams(this.filter(), this.selectedMapId(), this.sortState());

  }

}



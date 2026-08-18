import {
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Event as FmEvent, EventMap } from '../../src/app/core/api/api.models';
import { FmApiService } from '../../src/app/core/api/fm-api.service';
import { ConsolePageComponent } from '../../src/app/pages/console/console-page.component';
import { EventHistogramComponent } from '../../src/app/shared/event-histogram/event-histogram.component';

const MAP: EventMap = {
  id: 'map-1',
  name: 'Активные',
  query: '',
  isSystem: true,
  isPersonal: false,
  columns: [],
};

function sampleEvent(overrides: Partial<FmEvent> = {}): FmEvent {
  return {
    id: overrides.id ?? 'evt-1',
    status: 'new',
    severity: 'major',
    title: 'Disk full',
    description: '80%',
    repeatCount: 1,
    ciId: 'ci-1',
    nodeFqdn: 'db.example',
    systemName: 'demo',
    sourceId: 'src-1',
    createdAt: '2026-08-18T07:00:00.000Z',
    sourceAt: '2026-08-18T07:00:00.000Z',
    ...overrides,
  };
}

const MIXED_EVENTS: FmEvent[] = [
  sampleEvent({ id: 'evt-critical', severity: 'critical', title: 'CPU critical' }),
  sampleEvent({ id: 'evt-major', severity: 'major', title: 'Disk major' }),
  sampleEvent({ id: 'evt-minor', severity: 'minor', title: 'Latency minor' }),
  sampleEvent({ id: 'evt-warning', severity: 'warning', title: 'Disk warning' }),
];

@Component({ selector: 'app-event-histogram', standalone: true, template: '' })
class HistogramStub {
  @Input() eventTimestamps: unknown;
  @Output() timeRangeSelect = new EventEmitter();
}

async function setup(query: Record<string, string> = {}) {
  const queryParamMap$ = new BehaviorSubject<ParamMap>(convertToParamMap(query));
  const api = {
    listEventMaps: vi.fn().mockReturnValue(of([MAP])),
    listEvents: vi.fn().mockReturnValue(
      of({ items: MIXED_EVENTS, page: { page: 0, size: 50, totalElements: MIXED_EVENTS.length, totalPages: 1 } }),
    ),
    listUsers: vi.fn().mockReturnValue(of([])),
  };

  await TestBed.configureTestingModule({
    imports: [ConsolePageComponent],
    providers: [
      provideRouter([]),
      { provide: FmApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { queryParamMap: queryParamMap$ } },
    ],
  })
    .overrideComponent(ConsolePageComponent, {
      remove: { imports: [EventHistogramComponent] },
      add: { imports: [HistogramStub] },
    })
    .compileComponents();

  const fixture = TestBed.createComponent(ConsolePageComponent);
  fixture.detectChanges();
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement, queryParamMap$ };
}

function severityChipText(el: HTMLElement): string | null {
  const chip = el.querySelector('.chip');
  return chip?.textContent?.replace(/\s+/g, ' ').trim() ?? null;
}

function lastListEventsParams(api: { listEvents: ReturnType<typeof vi.fn> }): Record<string, unknown> | undefined {
  const calls = api.listEvents.mock.calls;
  return calls.at(-1)?.[0] as Record<string, unknown> | undefined;
}

describe('ConsolePageComponent severity query param', () => {
  const fixtures: ComponentFixture<ConsolePageComponent>[] = [];

  afterEach(() => {
    while (fixtures.length) {
      fixtures.pop()?.destroy();
    }
  });

  it.each(['critical', 'major', 'minor', 'warning'] as const)(
    'applies ?severity=%s as query-bar chip and listEvents param on first load',
    async (severity) => {
      const { fixture, api, el } = await setup({ severity });
      fixtures.push(fixture);

      expect(el.querySelector('.chip')).toBeTruthy();
      expect(severityChipText(el)).toMatch(new RegExp(`severity\\s*=\\s*${severity}`));
      expect(api.listEvents).toHaveBeenCalled();
      expect(api.listEvents.mock.calls[0][0]).toEqual(expect.objectContaining({ severity }));
    },
  );

  it('clears the severity chip and reloads listEvents without severity', async () => {
    const { fixture, api, el } = await setup({ severity: 'major' });
    fixtures.push(fixture);

    expect(el.querySelector('.chip')).toBeTruthy();
    expect(severityChipText(el)).toMatch(/severity\s*=\s*major/);
    expect(api.listEvents.mock.calls[0][0]).toEqual(expect.objectContaining({ severity: 'major' }));

    const remove = el.querySelector('.chip-remove') as HTMLButtonElement;
    expect(remove).toBeTruthy();
    remove.click();
    fixture.detectChanges();

    expect(el.querySelector('.chip')).toBeNull();
    expect(lastListEventsParams(api)).not.toHaveProperty('severity');
  });

  it('adds no severity chip when the query param is missing', async () => {
    const { fixture, api, el } = await setup();
    fixtures.push(fixture);

    expect(el.querySelector('.chip')).toBeNull();
    expect(api.listEvents).toHaveBeenCalled();
    expect(api.listEvents.mock.calls[0][0]).not.toHaveProperty('severity');
  });

  it('adds no severity chip for an unknown severity value', async () => {
    const { fixture, api, el } = await setup({ severity: 'not-a-severity' });
    fixtures.push(fixture);

    expect(el.querySelector('.chip')).toBeNull();
    expect(api.listEvents).toHaveBeenCalled();
    expect(api.listEvents.mock.calls[0][0]).not.toHaveProperty('severity');
  });
});

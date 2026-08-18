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

const PRODUCT_ID = '550e8400-e29b-41d4-a716-446655440000';

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

function chipTexts(el: HTMLElement): string[] {
  return Array.from(el.querySelectorAll('.chip')).map(
    (chip) => chip.textContent?.replace(/\s+/g, ' ').trim() ?? '',
  );
}

function lastListEventsParams(api: { listEvents: ReturnType<typeof vi.fn> }): Record<string, unknown> | undefined {
  const calls = api.listEvents.mock.calls;
  return calls.at(-1)?.[0] as Record<string, unknown> | undefined;
}

describe('ConsolePageComponent productId query param', () => {
  const fixtures: ComponentFixture<ConsolePageComponent>[] = [];

  afterEach(() => {
    while (fixtures.length) {
      fixtures.pop()?.destroy();
    }
  });

  it('applies a valid ?productId= UUID as a chip and listEvents param on first load', async () => {
    const { fixture, api, el } = await setup({ productId: PRODUCT_ID });
    fixtures.push(fixture);

    expect(chipTexts(el).some((text) => new RegExp(`productId\\s*=\\s*${PRODUCT_ID}`).test(text))).toBe(true);
    expect(api.listEvents).toHaveBeenCalled();
    expect(api.listEvents.mock.calls[0][0]).toEqual(expect.objectContaining({ productId: PRODUCT_ID }));

    const addBtn = el.querySelector('.btn-add') as HTMLButtonElement;
    addBtn.click();
    fixture.detectChanges();

    const fieldSelect = el.querySelector('.add-form select.sel:not(.narrow)') as HTMLSelectElement;
    expect(fieldSelect).toBeTruthy();
    const fieldValues = Array.from(fieldSelect.options).map((option) => option.value);
    expect(fieldValues).toEqual(['severity', 'status', 'title']);
    expect(fieldValues).not.toContain('productId');
  });

  it('clears the productId chip and reloads listEvents without productId', async () => {
    const { fixture, api, el } = await setup({ productId: PRODUCT_ID });
    fixtures.push(fixture);

    expect(chipTexts(el).some((text) => new RegExp(`productId\\s*=\\s*${PRODUCT_ID}`).test(text))).toBe(true);
    expect(api.listEvents.mock.calls[0][0]).toEqual(expect.objectContaining({ productId: PRODUCT_ID }));

    const remove = el.querySelector('.chip-remove') as HTMLButtonElement;
    expect(remove).toBeTruthy();
    remove.click();
    fixture.detectChanges();

    expect(el.querySelector('.chip')).toBeNull();
    expect(lastListEventsParams(api)).not.toHaveProperty('productId');
  });

  it.each([
    { name: 'missing', query: {} },
    { name: 'empty', query: { productId: '' } },
    { name: 'not-a-uuid', query: { productId: 'not-a-uuid' } },
  ])('adds no productId chip when the query param is $name', async ({ query }) => {
    const { fixture, api, el } = await setup(query);
    fixtures.push(fixture);

    expect(chipTexts(el).some((text) => text.includes('productId'))).toBe(false);
    expect(api.listEvents).toHaveBeenCalled();
    expect(api.listEvents.mock.calls[0][0]).not.toHaveProperty('productId');
  });

  it('applies ?productId= and ?severity= together as chips and listEvents params', async () => {
    const { fixture, api, el } = await setup({ productId: PRODUCT_ID, severity: 'major' });
    fixtures.push(fixture);

    const chips = chipTexts(el);
    expect(chips.some((text) => new RegExp(`productId\\s*=\\s*${PRODUCT_ID}`).test(text))).toBe(true);
    expect(chips.some((text) => /severity\s*=\s*major/.test(text))).toBe(true);
    expect(api.listEvents.mock.calls[0][0]).toEqual(
      expect.objectContaining({ productId: PRODUCT_ID, severity: 'major' }),
    );
  });
});

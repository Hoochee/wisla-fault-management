import {
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EventActionLog, Event as FmEvent, EventMap, User } from '../../src/app/core/api/api.models';
import { EventActionResult, FmApiService } from '../../src/app/core/api/fm-api.service';
import { ConsolePageComponent } from '../../src/app/pages/console/console-page.component';
import { EventHistogramComponent } from '../../src/app/shared/event-histogram/event-histogram.component';
import { QueryBarComponent } from '../../src/app/shared/query-bar/query-bar.component';

const EVENT_ID = 'evt-console-1';

const USER: User = {
  id: 'u-2',
  login: 'petrov',
  fullName: 'Петров П.П.',
  email: 'petrov@example.com',
  roleIds: [],
  team: 'noc',
  active: true,
};

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
    id: EVENT_ID,
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

function log(action: string): EventActionLog {
  return {
    id: `log-${action}`,
    eventId: EVENT_ID,
    action,
    userName: USER.fullName,
    timestamp: '2026-08-18T08:00:00.000Z',
    details: action,
  };
}

@Component({ selector: 'app-query-bar', standalone: true, template: '' })
class QueryBarStub {
  @Input() filter: unknown;
  @Output() filterChange = new EventEmitter();
  @Output() refresh = new EventEmitter();
}

@Component({ selector: 'app-event-histogram', standalone: true, template: '' })
class HistogramStub {
  @Input() eventTimestamps: unknown;
  @Output() timeRangeSelect = new EventEmitter();
}

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as
    | HTMLButtonElement
    | undefined;
}

async function setup(items: FmEvent[] = [sampleEvent()]) {
  const api = {
    listEventMaps: vi.fn().mockReturnValue(of([MAP])),
    listEvents: vi.fn().mockReturnValue(of({ items, page: { page: 0, size: 50, totalElements: items.length, totalPages: 1 } })),
    performEventAction: vi.fn().mockImplementation((id: string, action: string) => {
      const event = items.find((e) => e.id === id) ?? sampleEvent();
      const result: EventActionResult = { event, logEntry: log(action) };
      return of(result);
    }),
    listUsers: vi.fn().mockReturnValue(of([USER])),
  };

  await TestBed.configureTestingModule({
    imports: [ConsolePageComponent],
    providers: [provideRouter([]), { provide: FmApiService, useValue: api }],
  })
    .overrideComponent(ConsolePageComponent, {
      remove: { imports: [QueryBarComponent, EventHistogramComponent] },
      add: { imports: [QueryBarStub, HistogramStub] },
    })
    .compileComponents();

  const fixture = TestBed.createComponent(ConsolePageComponent);
  fixture.detectChanges();
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement };
}

function selectFirstRow(fixture: ComponentFixture<ConsolePageComponent>): HTMLElement {
  const el = fixture.nativeElement as HTMLElement;
  const row = el.querySelector('tbody tr') as HTMLTableRowElement;
  row.click();
  fixture.detectChanges();
  return el.querySelector('.detail-panel') as HTMLElement;
}

describe('ConsolePageComponent selected detail-panel', () => {
  const fixtures: ComponentFixture<ConsolePageComponent>[] = [];

  afterEach(() => {
    while (fixtures.length) {
      fixtures.pop()?.destroy();
    }
  });

  it('Console bottom panel acts on the selected row', async () => {
    const { fixture, api, el } = await setup();
    fixtures.push(fixture);

    expect(el.querySelector('.detail-panel')).toBeNull();
    const panel = selectFirstRow(fixture);

    expect(panel).toBeTruthy();
    expect(buttonByText(panel, 'Подтвердить')).toBeTruthy();
    expect(buttonByText(panel, 'Комментарий')).toBeTruthy();
    expect(panel.textContent).toContain('Назначить');
    expect(buttonByText(panel, '15')).toBeTruthy();
    expect(buttonByText(panel, '30')).toBeTruthy();
    expect(buttonByText(panel, '60')).toBeTruthy();
    expect(buttonByText(panel, 'Принять в работу')).toBeTruthy();
    expect(buttonByText(panel, 'Закрыть')).toBeTruthy();

    buttonByText(panel, 'Подтвердить')!.click();
    fixture.detectChanges();
    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'ack');

    expect(el.querySelector('.bulk-toolbar')).toBeNull();
    expect(el.querySelector('.context-menu')).toBeNull();
    expect(el.querySelector('[contextmenu]')).toBeNull();
  });

  it('Operator comments from the console bottom panel', async () => {
    const { fixture, api } = await setup();
    fixtures.push(fixture);
    const panel = selectFirstRow(fixture);

    buttonByText(panel, 'Комментарий')!.click();
    fixture.detectChanges();

    const textarea = panel.querySelector('textarea') as HTMLTextAreaElement;
    textarea.value = 'Комментарий с панели';
    textarea.dispatchEvent(new window.Event('input', { bubbles: true }));
    fixture.detectChanges();

    buttonByText(panel, 'Отправить')!.click();
    fixture.detectChanges();

    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'comment', {
      comment: 'Комментарий с панели',
    });
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { EventActionLog, EventDetail, User } from '../../src/app/core/api/api.models';
import { EventActionResult, FmApiService } from '../../src/app/core/api/fm-api.service';
import { EventCardPageComponent } from '../../src/app/pages/console/event-card-page.component';

const EVENT_ID = 'evt-card-1';

const USER: User = {
  id: 'u-ack',
  login: 'ivanov',
  fullName: 'Иванов И.И.',
  email: 'ivanov@example.com',
  roleIds: [],
  team: 'noc',
  active: true,
};

function log(action: string, id: string): EventActionLog {
  return {
    id,
    eventId: EVENT_ID,
    action,
    userName: 'Иванов И.И.',
    timestamp: '2026-08-18T08:00:00.000Z',
    details: `${action} details`,
  };
}

function eventDetail(overrides: Partial<EventDetail> = {}): EventDetail {
  return {
    id: EVENT_ID,
    status: 'new',
    severity: 'critical',
    title: 'CPU high',
    description: 'load',
    repeatCount: 1,
    ciId: 'ci-1',
    nodeFqdn: 'host.example',
    systemName: 'demo',
    sourceId: 'src-1',
    createdAt: '2026-08-18T07:00:00.000Z',
    sourceAt: '2026-08-18T07:00:00.000Z',
    actionLogs: [log('ack', 'l1'), log('comment', 'l2'), log('assign', 'l3'), log('silence', 'l4')],
    ...overrides,
  };
}

function result(event: Event, action: string): EventActionResult {
  return { event, logEntry: log(action, `log-${action}`) };
}

function buttonByText(root: HTMLElement, text: string): HTMLButtonElement | undefined {
  return Array.from(root.querySelectorAll('button')).find((b) => b.textContent?.trim() === text) as
    | HTMLButtonElement
    | undefined;
}

async function setup(detail: EventDetail = eventDetail()) {
  const api = {
    getEvent: vi.fn().mockReturnValue(of(detail)),
    performEventAction: vi.fn().mockImplementation((id: string, action: string) => of(result({ ...detail, id }, action))),
    listUsers: vi.fn().mockReturnValue(of([USER])),
  };

  await TestBed.configureTestingModule({
    imports: [EventCardPageComponent],
    providers: [
      provideRouter([]),
      { provide: FmApiService, useValue: api },
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => EVENT_ID } } } },
    ],
  }).compileComponents();

  const fixture = TestBed.createComponent(EventCardPageComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, api, el: fixture.nativeElement as HTMLElement };
}

describe('EventCardPageComponent duty actions', () => {
  it('Card shows duty action buttons', async () => {
    const { el } = await setup();

    expect(buttonByText(el, 'Принять в работу')).toBeTruthy();
    expect(buttonByText(el, 'Закрыть')).toBeTruthy();
    expect(buttonByText(el, 'Подтвердить')).toBeTruthy();
    expect(buttonByText(el, 'Комментарий')).toBeTruthy();
    expect(el.textContent).toContain('Назначить');
    expect(buttonByText(el, '15')).toBeTruthy();
    expect(buttonByText(el, '30')).toBeTruthy();
    expect(buttonByText(el, '60')).toBeTruthy();
    expect(el.querySelector('.bulk-toolbar')).toBeNull();
  });

  it('Journal labels are human-readable', async () => {
    const { fixture, el } = await setup();

    buttonByText(el, 'Журнал действий')!.click();
    fixture.detectChanges();

    const labels = Array.from(el.querySelectorAll('.log-action')).map((n) => n.textContent?.trim());
    expect(labels).toContain('Подтверждено');
    expect(labels).toContain('Комментарий');
    expect(labels).toContain('Назначено');
    expect(labels).toContain('Скрыто');
    expect(labels).not.toContain('ack');
    expect(labels).not.toContain('assign');
    expect(labels).not.toContain('silence');
  });

  it('Card indicates ack and silence', async () => {
    const { el } = await setup(
      eventDetail({
        acknowledgedAt: '2026-08-18T08:15:00.000Z',
        acknowledgedByUserId: USER.id,
        silencedUntil: '2099-01-01T12:00:00.000Z',
        silencedByUserId: USER.id,
      }),
    );

    expect(el.textContent).toContain('Подтверждено');
    expect(el.textContent).toContain(USER.fullName);
    expect(el.textContent).toMatch(/Скрыто до/);
  });

  it('Operator comments from the card', async () => {
    const commented = eventDetail({
      actionLogs: [log('comment', 'l-new')],
    });
    const { fixture, api, el } = await setup();
    api.getEvent.mockReturnValue(of(commented));

    buttonByText(el, 'Комментарий')!.click();
    fixture.detectChanges();

    const textarea = el.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea).toBeTruthy();
    textarea.value = 'Проверил на площадке';
    textarea.dispatchEvent(new window.Event('input', { bubbles: true }));
    fixture.detectChanges();

    buttonByText(el, 'Отправить')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'comment', {
      comment: 'Проверил на площадке',
    });
    expect(el.textContent).toContain('Комментарий');
    expect(el.querySelector('.log-action')?.textContent?.trim()).toBe('Комментарий');
  });

  it('Assign picker uses active admin users', async () => {
    const { fixture, api, el } = await setup();

    expect(api.listUsers).toHaveBeenCalledWith({ active: true });
    const select = el.querySelector('select.assign-select') as HTMLSelectElement;
    expect(select).toBeTruthy();
    expect(Array.from(select.options).some((o) => o.textContent?.includes(USER.fullName))).toBe(true);

    select.value = USER.id;
    select.dispatchEvent(new window.Event('change', { bubbles: true }));
    fixture.detectChanges();

    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'assign', {
      assignedUserId: USER.id,
    });
  });

  it('Take and close still work after comment UI', async () => {
    const { fixture, api, el } = await setup();

    buttonByText(el, 'Принять в работу')!.click();
    fixture.detectChanges();
    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'take');

    buttonByText(el, 'Закрыть')!.click();
    fixture.detectChanges();
    expect(api.performEventAction).toHaveBeenCalledWith(EVENT_ID, 'close');
  });
});

import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { PushToastService } from '../../src/app/core/notifications/push-toast.service';
import { FmApiService } from '../../src/app/core/api/fm-api.service';
import { PushNotification } from '../../src/app/core/api/api.models';

function note(id: string, createdAt = `2026-08-14T10:00:0${id}.000Z`): PushNotification {
  return {
    id,
    ruleId: 'rule-1',
    eventId: `event-${id}`,
    title: `Title ${id}`,
    message: `Message ${id}`,
    createdAt,
  };
}

describe('PushToastService', () => {
  let api: { getPushNotifications: ReturnType<typeof vi.fn> };
  let service: PushToastService;

  beforeEach(() => {
    api = { getPushNotifications: vi.fn(() => of({ items: [] })) };
    TestBed.configureTestingModule({
      providers: [
        PushToastService,
        { provide: FmApiService, useValue: api },
        { provide: Router, useValue: { navigate: vi.fn() } },
      ],
    });
    service = TestBed.inject(PushToastService);
  });

  afterEach(() => {
    service.stop();
  });

  it('keeps at most 3 visible toasts and drops the oldest when a fourth arrives', () => {
    api.getPushNotifications.mockReturnValue(
      of({ items: [note('1'), note('2'), note('3'), note('4')] }),
    );

    service.start();

    expect(service.toasts().map((t) => t.id)).toEqual(['2', '3', '4']);
  });
});

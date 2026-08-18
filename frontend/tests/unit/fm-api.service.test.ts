import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ApiClientService } from '../../src/app/core/api/api-client.service';
import { FmApiService } from '../../src/app/core/api/fm-api.service';

describe('FmApiService duty actions', () => {
  let service: FmApiService;
  let api: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = {
      get: vi.fn().mockReturnValue(of({ items: [] })),
      post: vi.fn().mockReturnValue(of({ event: {}, logEntry: {} })),
    };
    TestBed.configureTestingModule({
      providers: [FmApiService, { provide: ApiClientService, useValue: api }],
    });
    service = TestBed.inject(FmApiService);
  });

  it('performEventAction posts { action }', () => {
    service.performEventAction('evt-1', 'ack').subscribe();
    expect(api.post).toHaveBeenCalledWith('/events/evt-1/actions', { action: 'ack' });
  });

  it('performEventAction posts optional comment, assignedUserId, and silenceMinutes', () => {
    service.performEventAction('evt-1', 'comment', { comment: 'seen' }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/events/evt-1/actions', {
      action: 'comment',
      comment: 'seen',
    });

    service.performEventAction('evt-1', 'assign', { assignedUserId: 'u-9' }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/events/evt-1/actions', {
      action: 'assign',
      assignedUserId: 'u-9',
    });

    service.performEventAction('evt-1', 'silence', { silenceMinutes: 30 }).subscribe();
    expect(api.post).toHaveBeenCalledWith('/events/evt-1/actions', {
      action: 'silence',
      silenceMinutes: 30,
    });
  });

  it('listUsers({ active: true }) calls GET /admin/users?active=true', () => {
    service.listUsers({ active: true }).subscribe();
    expect(api.get).toHaveBeenCalledWith('/admin/users', { active: true });
  });

  it('listUsers() without filter still calls GET /admin/users', () => {
    service.listUsers().subscribe();
    expect(api.get).toHaveBeenCalledWith('/admin/users', undefined);
  });
});

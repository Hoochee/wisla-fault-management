import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { authGuard } from '../../src/app/core/auth/auth.guard';
import { AuthService } from '../../src/app/core/auth/auth.service';

describe('authGuard', () => {
  let auth: { isAuthenticated: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = { isAuthenticated: vi.fn() };
    router = { createUrlTree: vi.fn(() => ({} as UrlTree)) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('allows authenticated users', () => {
    auth.isAuthenticated.mockReturnValue(true);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never),
    );
    expect(result).toBe(true);
  });

  it('redirects unauthenticated users to login', () => {
    auth.isAuthenticated.mockReturnValue(false);
    const tree = {} as UrlTree;
    router.createUrlTree.mockReturnValue(tree);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, {} as never),
    );
    expect(router.createUrlTree).toHaveBeenCalledWith(['/login']);
    expect(result).toBe(tree);
  });
});
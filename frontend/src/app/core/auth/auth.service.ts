import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, tap, map, switchMap, catchError } from 'rxjs';
import { ApiClientService } from '../api/api-client.service';
import { LoginRequest, LoginResponse, ProductAdmin, User } from '../api/api.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiClientService);
  private readonly router = inject(Router);

  readonly currentUser = signal<User | null>(null);
  readonly permissions = signal<string[]>([]);
  readonly isAdmin = computed(() => this.permissions().includes('admin'));

  isAuthenticated(): boolean {
    return !!this.api.getToken();
  }

  login(credentials: LoginRequest): Observable<User> {
    return this.api.post<LoginResponse>('/auth/login', credentials).pipe(
      tap((res) => {
        this.api.setToken(res.accessToken);
        this.currentUser.set(res.user);
      }),
      switchMap((res) => this.refreshPermissions().pipe(map(() => res.user))),
    );
  }

  logout(): void {
    this.api.setToken(null);
    this.currentUser.set(null);
    this.permissions.set([]);
    void this.router.navigate(['/login']);
  }

  loadCurrentUser(): Observable<User | null> {
    if (!this.isAuthenticated()) {
      return of(null);
    }
    return this.api.get<User>('/auth/me').pipe(
      tap((user) => this.currentUser.set(user)),
      switchMap((user) => this.refreshPermissions().pipe(map(() => user))),
      catchError(() => {
        this.currentUser.set(null);
        this.permissions.set([]);
        return of(null);
      }),
    );
  }

  /** Resolves admin UI access by probing admin products API (backend requireAdmin). */
  private refreshPermissions(): Observable<string[]> {
    return this.api.get<ProductAdmin[]>('/admin/products').pipe(
      map(() => ['admin']),
      catchError((err: HttpErrorResponse) => of(err.status === 403 ? [] : [])),
      tap((perms) => this.permissions.set(perms)),
    );
  }
}

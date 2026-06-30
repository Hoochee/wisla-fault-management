import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Router } from '@angular/router';

export const API_BASE_URL = '/api/v1';
export const TOKEN_STORAGE_KEY = 'fm_access_token';

@Injectable({ providedIn: 'root' })
export class ApiClientService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  getToken(): string | null {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
  }

  setToken(token: string | null): void {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token);
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  }

  private authHeaders(): HttpHeaders {
    const token = this.getToken();
    let headers = new HttpHeaders({ 'Content-Type': 'application/json' });
    if (token) {
      headers = headers.set('Authorization', `Bearer ${token}`);
    }
    return headers;
  }

  get<T>(path: string, params?: Record<string, string | number | boolean | string[]>): Observable<T> {
    return this.http
      .get<T>(`${API_BASE_URL}${path}`, {
        headers: this.authHeaders(),
        params: this.toHttpParams(params),
      })
      .pipe(catchError((err) => this.handleError(err)));
  }

  post<T>(path: string, body?: unknown): Observable<T> {
    return this.http
      .post<T>(`${API_BASE_URL}${path}`, body ?? {}, { headers: this.authHeaders() })
      .pipe(catchError((err) => this.handleError(err)));
  }

  patch<T>(path: string, body: unknown): Observable<T> {
    return this.http
      .patch<T>(`${API_BASE_URL}${path}`, body, { headers: this.authHeaders() })
      .pipe(catchError((err) => this.handleError(err)));
  }

  delete<T>(path: string): Observable<T> {
    return this.http
      .delete<T>(`${API_BASE_URL}${path}`, { headers: this.authHeaders() })
      .pipe(catchError((err) => this.handleError(err)));
  }

  private toHttpParams(params?: Record<string, string | number | boolean | string[]>): HttpParams | undefined {
    if (!params) return undefined;
    let httpParams = new HttpParams();
    for (const [key, value] of Object.entries(params)) {
      if (Array.isArray(value)) {
        value.forEach((v) => (httpParams = httpParams.append(key, v)));
      } else {
        httpParams = httpParams.set(key, String(value));
      }
    }
    return httpParams;
  }

  private handleError(err: HttpErrorResponse): Observable<never> {
    if (err.status === 401 && !this.router.url.startsWith('/login')) {
      this.setToken(null);
      void this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
    }
    return throwError(() => err);
  }
}

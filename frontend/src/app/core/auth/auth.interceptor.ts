import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { API_BASE_URL, TOKEN_STORAGE_KEY } from '../api/api-client.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);

  const isApi = req.url.startsWith(API_BASE_URL) || req.url.startsWith('/api/');
  const isLogin = req.url.includes('/auth/login');

  let headers = req.headers;
  if (token && isApi && !isLogin) {
    headers = headers.set('Authorization', `Bearer ${token}`);
  }

  return next(req.clone({ headers })).pipe(
    catchError((err) => {
      if (err.status === 401 && !router.url.startsWith('/login')) {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};

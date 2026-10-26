import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

const serviceUrls = Object.values(environment);

// The bearer token and correlation id are only attached to calls to our own services.
export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  if (!serviceUrls.some((u) => req.url.startsWith(u))) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.token();
  const headers: Record<string, string> = { 'X-Correlation-Id': crypto.randomUUID() };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }
  return next(req.clone({ setHeaders: headers })).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && token) {
        auth.logout();
        router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};

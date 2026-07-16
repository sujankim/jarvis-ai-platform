import { HttpInterceptorFn, HttpErrorResponse }
  from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { StorageService } from '../services/storage.service';

/**
 * Functional HTTP interceptor — Angular 22 standard.
 *
 * Reference:
 * https://angular.dev/guide/http/interceptors
 *
 * Two responsibilities:
 * 1. Attach JWT to every outgoing request
 * 2. Redirect to /login on 401 Unauthorized
 *
 * Why functional not class-based:
 * Angular 22 recommends functional interceptors.
 * They use inject() instead of constructor injection.
 * No need to implement HttpInterceptor interface.
 * Registered directly in app.config.ts.
 */
export const authInterceptor: HttpInterceptorFn =
  (req, next) => {

    const storage = inject(StorageService);
    const router  = inject(Router);
    const token   = storage.getToken();

    // Clone request and attach JWT if token exists.
    // Never modify the original request object.
    const authReq = token
      ? req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      })
      : req;

    return next(authReq).pipe(
      catchError((error: HttpErrorResponse) => {

        // 401 Unauthorized → token expired or invalid
        // Clear stored data and redirect to login
        if (error.status === 401) {
          storage.clearAll();
          router.navigate(['/login']);
        }

        return throwError(() => error);
      })
    );
  };

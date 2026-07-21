import {
  ApplicationConfig,
  provideZoneChangeDetection
} from '@angular/core';
import {
  provideRouter,
  withComponentInputBinding
} from '@angular/router';
import {
  provideHttpClient,
  withInterceptors
} from '@angular/common/http';
import {
  provideMarkdown
} from 'ngx-markdown';
import { routes } from './app.routes';
import { authInterceptor }
  from './core/interceptors/auth.interceptor';

/**
 * Angular 22 application configuration.
 *
 * No animations provider needed.
 * Angular Material 22 uses CSS-based animations by default.
 * provideAnimations() and provideAnimationsAsync() are both
 * deprecated in Angular 22 — removed entirely.
 *
 * withInterceptors() — registers functional interceptors.
 * withComponentInputBinding() — route params as component inputs.
 * fetch is default in Angular 22 — withFetch() not needed.
 *
 * Reference:
 * https://angular.dev/guide/animations
 * https://angular.dev/guide/http/setup
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({
      eventCoalescing: true
    }),
    provideRouter(
      routes,
      withComponentInputBinding()
    ),
    provideHttpClient(
      withInterceptors([authInterceptor])
    ),
    provideMarkdown()
  ]
};

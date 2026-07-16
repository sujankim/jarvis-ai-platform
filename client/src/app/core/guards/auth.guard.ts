import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Functional route guard — Angular 22 standard.
 *
 * Protects all routes except /login.
 * Redirects to /login if not authenticated.
 *
 * Reference:
 * https://angular.dev/guide/routing/common-router-tasks
 *
 * Registered in app.routes.ts on every protected route.
 */
export const authGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return true;
  }

  // Not logged in → redirect to login
  // returnUrl could be added here in the future
  return router.createUrlTree(['/login']);
};

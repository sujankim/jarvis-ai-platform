import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

/**
 * Application routes.
 *
 * All routes except /login are protected by authGuard.
 * All feature routes use lazy loading.
 *
 * Lazy loading: each page loads only when navigated to.
 * Faster initial bundle. Each feature is independent.
 *
 * Reference:
 * https://angular.dev/guide/routing/common-router-tasks
 */
export const routes: Routes = [

  // Public
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login')
        .then(m => m.Login)
  },

  // Protected — all require valid JWT
  {
    path: 'chat',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/chat/chat')
        .then(m => m.Chat)
  },
  {
    path: 'memory',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/memory/memory')
        .then(m => m.Memory)
  },
  {
    path: 'documents',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/documents/documents')
        .then(m => m.Documents)
  },
  {
    path: 'agents',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/agents/agents')
        .then(m => m.Agents)
  },
  {
    path: 'voice',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/voice/voice')
        .then(m => m.Voice)
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/settings')
        .then(m => m.Settings)
  },

  // Default redirects
  {
    path: '',
    redirectTo: 'chat',
    pathMatch: 'full'
  },
  {
    path: '**',
    redirectTo: 'chat'
  }
];

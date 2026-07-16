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

  // Public — no shell
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login')
        .then(m => m.Login)
  },

  // Protected — all inside shell layout
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shared/components/shell/shell')
        .then(m => m.Shell),
    children: [
      {
        path: 'chat',
        loadComponent: () =>
          import('./features/chat/chat')
            .then(m => m.Chat)
      },
      {
        path: 'memory',
        loadComponent: () =>
          import('./features/memory/memory')
            .then(m => m.Memory)
      },
      {
        path: 'documents',
        loadComponent: () =>
          import('./features/documents/documents')
            .then(m => m.Documents)
      },
      {
        path: 'agents',
        loadComponent: () =>
          import('./features/agents/agents')
            .then(m => m.Agents)
      },
      {
        path: 'voice',
        loadComponent: () =>
          import('./features/voice/voice')
            .then(m => m.Voice)
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings')
            .then(m => m.Settings)
      },
      {
        path: '',
        redirectTo: 'chat',
        pathMatch: 'full'
      }
    ]
  },

  // Fallback
  {
    path: '**',
    redirectTo: 'chat'
  }
];

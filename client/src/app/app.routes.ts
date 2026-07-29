import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './core/services/auth.service';

/**
 * Redirects already-authenticated users away from /login.
 * Prevents logged-in users seeing the login page.
 */
const guestGuard = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  if (auth.isLoggedIn()) {
    return router.createUrlTree(['/chat']);
  }
  return true;
};

export const routes: Routes = [

  // Public — redirect to /chat if already logged in
  {
    path: 'login',
    canActivate: [guestGuard],
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
        title: 'Chat — Jarvis',
        loadComponent: () =>
          import('./features/chat/chat')
            .then(m => m.Chat)
      },
      {
        path: 'memory',
        title: 'Memory — Jarvis',
        loadComponent: () =>
          import('./features/memory/memory')
            .then(m => m.MemoryPage)
      },
      {
        path: 'documents',
        title: 'Documents — Jarvis',
        loadComponent: () =>
          import('./features/documents/documents')
            .then(m => m.Documents)
      },
      {
        path: 'agents',
        title: 'Agents — Jarvis',
        loadComponent: () =>
          import('./features/agents/agents')
            .then(m => m.Agents)
      },
      {
        path: 'voice',
        title: 'Voice — Jarvis',
        loadComponent: () =>
          import('./features/voice/voice')
            .then(m => m.Voice)
      },
      {
        path: 'settings',
        title: 'Settings — Jarvis',
        loadComponent: () =>
          import('./features/settings/settings')
            .then(m => m.settings)
      },
      {
        path: '',
        redirectTo: 'chat',
        pathMatch: 'full'
      }
    ]
  },

  // 404 — friendly not-found page
  {
    path: 'not-found',
    title: 'Not Found — Jarvis',
    loadComponent: () =>
      import('./features/not-found/not-found')
        .then(m => m.NotFound)
  },

  // Catch all unknown routes
  {
    path: '**',
    redirectTo: 'not-found'
  }
];

import {
  Component,
  inject,
  signal
} from '@angular/core';
import {
  RouterOutlet,
  RouterLink,
  RouterLinkActive
} from '@angular/router';
import { AuthService }
  from '../../../core/services/auth.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

/**
 * App shell — wraps all protected pages.
 * Contains: sidebar navigation + top bar + content area.
 * All protected routes render inside <router-outlet> here.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss'
})
export class Shell {

  private readonly auth = inject(AuthService);

  // Sidebar collapse state
  readonly sidebarCollapsed = signal(false);

  // Current user info from AuthService signals
  readonly displayName = this.auth.displayName;
  readonly username    = this.auth.username;
  readonly isAdmin     = this.auth.isAdmin;

  // Navigation items
  readonly navItems: NavItem[] = [
    {
      path: '/chat',
      label: 'Chat',
      icon: 'chat_bubble_outline'
    },
    {
      path: '/memory',
      label: 'Memory',
      icon: 'psychology'
    },
    {
      path: '/documents',
      label: 'Documents',
      icon: 'folder_open'
    },
    {
      path: '/agents',
      label: 'Agents',
      icon: 'smart_toy'
    },
    {
      path: '/voice',
      label: 'Voice',
      icon: 'mic'
    },
    {
      path: '/settings',
      label: 'Settings',
      icon: 'settings'
    }
  ];

  toggleSidebar(): void {
    this.sidebarCollapsed.update(v => !v);
  }

  logout(): void {
    this.auth.logout();
  }
}

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
import { ThemeService }
  from '../../../core/services/theme.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

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

  private readonly auth  = inject(AuthService);
  readonly theme         = inject(ThemeService);

  readonly sidebarCollapsed = signal(false);

  readonly displayName = this.auth.displayName;
  readonly username    = this.auth.username;
  readonly isAdmin     = this.auth.isAdmin;

  readonly navItems: NavItem[] = [
    { path: '/chat',      label: 'Chat',      icon: 'chat_bubble_outline' },
    { path: '/memory',    label: 'Memory',    icon: 'psychology'          },
    { path: '/documents', label: 'Documents', icon: 'folder_open'         },
    { path: '/agents',    label: 'Agents',    icon: 'smart_toy'           },
    { path: '/voice',     label: 'Voice',     icon: 'mic'                 },
    { path: '/settings',  label: 'Settings',  icon: 'settings'            }
  ];

  toggleSidebar(): void {
    this.sidebarCollapsed.update(v => !v);
  }

  logout(): void {
    this.auth.logout();
  }
}

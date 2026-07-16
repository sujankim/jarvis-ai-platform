import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root application shell.
 * Contains only <router-outlet>.
 * All layout (sidebar, topbar) lives inside
 * each feature component — not here.
 * Login page is full-screen with no shell.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  readonly title = 'Jarvis AI Platform';
}

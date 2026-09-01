import { Injectable, signal, effect } from '@angular/core';

/**
 * Manages dark/light theme toggle.
 *
 * Theme stored in localStorage so it persists
 * across page refreshes.
 *
 * Applies .light-theme class to document.body.
 * Our _light.scss targets body.light-theme.
 * Default is dark — no class needed on body.
 */
@Injectable({
  providedIn: 'root'
})
export class ThemeService {

  private readonly STORAGE_KEY = 'jarvis_theme';

  readonly isDark = signal<boolean>(
    this.loadTheme()
  );

  constructor() {
    // Apply theme to body whenever signal changes
    effect(() => {
      if (this.isDark()) {
        document.body.classList.remove('light-theme');
      } else {
        document.body.classList.add('light-theme');
      }
      localStorage.setItem(
        this.STORAGE_KEY,
        this.isDark() ? 'dark' : 'light'
      );
    });
  }

  toggle(): void {
    this.isDark.update(v => !v);
  }

  private loadTheme(): boolean {
    const stored =
      localStorage.getItem(this.STORAGE_KEY);
    // Default to dark if no preference stored
    return stored !== 'light';
  }
}

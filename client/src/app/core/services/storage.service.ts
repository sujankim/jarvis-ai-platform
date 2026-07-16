import { Injectable } from '@angular/core';

/**
 * Centralizes all localStorage access.
 *
 * Why a service instead of direct localStorage calls:
 * - Single place to change storage keys
 * - Easier to mock in tests
 * - Type-safe access to stored values
 */
@Injectable({
  providedIn: 'root'
})
export class StorageService {

  private readonly TOKEN_KEY = 'jarvis_access_token';
  private readonly USER_KEY  = 'jarvis_user';

  // ── Token ─────────────────────────────────────

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
  }

  removeToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
  }

  hasToken(): boolean {
    return this.getToken() !== null;
  }

  // ── User ──────────────────────────────────────

  getUser(): import('../models/auth.model').UserInfo | null {
    const raw = localStorage.getItem(this.USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  setUser(
    user: import('../models/auth.model').UserInfo
  ): void {
    localStorage.setItem(
      this.USER_KEY,
      JSON.stringify(user)
    );
  }

  removeUser(): void {
    localStorage.removeItem(this.USER_KEY);
  }

  // ── Clear all ─────────────────────────────────

  clearAll(): void {
    this.removeToken();
    this.removeUser();
  }
}

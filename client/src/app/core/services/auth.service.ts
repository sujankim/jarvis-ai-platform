import { Injectable, inject, signal, computed }
  from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { StorageService } from './storage.service';
import {
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  RegisterResponse,
  UserInfo
} from '../models/auth.model';
import { ApiResponse } from '../models/api.model';

/**
 * Handles all authentication state and API calls.
 *
 * State managed with Angular Signals (Angular 22).
 * HTTP calls return Observables (RxJS).
 *
 * currentUser signal — reactive source of truth for auth state.
 * isLoggedIn computed — derived from currentUser signal.
 * isAdmin computed   — derived from currentUser signal.
 *
 * Reference:
 * https://angular.dev/guide/signals
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly http    = inject(HttpClient);
  private readonly router  = inject(Router);
  private readonly storage = inject(StorageService);

  // ── Signals ───────────────────────────────────

  // Initialize from localStorage so page refresh
  // does not log the user out
  private readonly _currentUser =
    signal<UserInfo | null>(
      this.storage.getUser()
    );

  // Public read-only computed signals
  readonly currentUser = this._currentUser.asReadonly();

  readonly isLoggedIn = computed(() =>
    this._currentUser() !== null
  );

  readonly isAdmin = computed(() =>
    this._currentUser()?.role === 'ADMIN'
  );

  readonly username = computed(() =>
    this._currentUser()?.username ?? ''
  );

  readonly displayName = computed(() =>
    this._currentUser()?.displayName ?? ''
  );

  // ── Login ─────────────────────────────────────

  /**
   * POST /api/v1/auth/login
   *
   * On success:
   * - Stores JWT in localStorage via StorageService
   * - Stores UserInfo in localStorage
   * - Updates currentUser signal
   */
  login(
    request: LoginRequest
  ): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(
        '/api/v1/auth/login',
        request
      )
      .pipe(
        tap(response => {
          this.storage.setToken(response.accessToken);
          this.storage.setUser(response.user);
          this._currentUser.set(response.user);
        })
      );
  }

  // ── Register ──────────────────────────────────

  /**
   * POST /api/v1/auth/register
   *
   * Returns RegisterResponse.
   * Does NOT auto-login — user must login separately.
   */
  register(
    request: RegisterRequest
  ): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(
      '/api/v1/auth/register',
      request
    );
  }

  // ── Logout ────────────────────────────────────

  /**
   * Clears all stored auth data and redirects to login.
   * No API call needed — JWT is stateless.
   */
  logout(): void {
    this.storage.clearAll();
    this._currentUser.set(null);
    this.router.navigate(['/login']);
  }
}

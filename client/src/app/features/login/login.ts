import {
  Component,
  inject,
  signal,
  computed
} from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators
} from '@angular/forms';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';

/**
 * Login page — public route, no auth guard.
 *
 * Two modes: login and register — toggled by tab.
 * Reactive forms with typed validation.
 * Redirects to /chat on successful login or register.
 *
 * Angular 22: standalone, inject(), signals, @if @for.
 */
@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {

  private readonly auth    = inject(AuthService);
  private readonly router  = inject(Router);
  private readonly fb      = inject(FormBuilder);

  // ── Mode toggle ───────────────────────────────
  readonly mode =
    signal<'login' | 'register'>('login');

  readonly isLoginMode = computed(() =>
    this.mode() === 'login'
  );

  // ── State signals ─────────────────────────────
  readonly isLoading = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly successMessage = signal<string | null>(null);

  // ── Forms ─────────────────────────────────────

  readonly loginForm = this.fb.group({
    username: ['', [
      Validators.required,
      Validators.minLength(3)
    ]],
    password: ['', [
      Validators.required,
      Validators.minLength(8)
    ]]
  });

  readonly registerForm = this.fb.group({
    username: ['', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(50)
    ]],
    email: ['', [
      Validators.required,
      Validators.email
    ]],
    password: ['', [
      Validators.required,
      Validators.minLength(8)
    ]],
    displayName: ['']
  });

  // ── Mode switch ───────────────────────────────

  switchMode(mode: 'login' | 'register'): void {
    this.mode.set(mode);
    this.errorMessage.set(null);
    this.successMessage.set(null);
    this.loginForm.reset();
    this.registerForm.reset();
  }

  // ── Login ─────────────────────────────────────

  onLogin(): void {
    if (this.loginForm.invalid || this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { username, password } =
      this.loginForm.value;

    this.auth.login({
      username: username!,
      password: password!
    }).subscribe({
      next: () => {
        this.router.navigate(['/chat']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          err.status === 401
            ? 'Invalid username or password.'
            : 'Login failed. Please try again.'
        );
      }
    });
  }

  // ── Register ──────────────────────────────────

  onRegister(): void {
    if (this.registerForm.invalid || this.isLoading()) {
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const { username, email, password, displayName } =
      this.registerForm.value;

    this.auth.register({
      username: username!,
      email: email!,
      password: password!,
      displayName: displayName || undefined
    }).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.successMessage.set(
          'Account created! Please log in.'
        );
        this.switchMode('login');
        this.loginForm.patchValue({
          username: username!
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          err.status === 409
            ? 'Username or email already taken.'
            : 'Registration failed. Please try again.'
        );
      }
    });
  }

  // ── Helpers ───────────────────────────────────

  getLoginError(field: string): string | null {
    const control = this.loginForm.get(field);
    if (!control?.invalid || !control?.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return `${field} is required.`;
    }
    if (control.hasError('minlength')) {
      const min =
        control.errors?.['minlength'].requiredLength;
      return `Minimum ${min} characters.`;
    }
    return null;
  }

  getRegisterError(field: string): string | null {
    const control = this.registerForm.get(field);
    if (!control?.invalid || !control?.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return `${field} is required.`;
    }
    if (control.hasError('email')) {
      return 'Invalid email address.';
    }
    if (control.hasError('minlength')) {
      const min =
        control.errors?.['minlength'].requiredLength;
      return `Minimum ${min} characters.`;
    }
    if (control.hasError('maxlength')) {
      const max =
        control.errors?.['maxlength'].requiredLength;
      return `Maximum ${max} characters.`;
    }
    return null;
  }
}

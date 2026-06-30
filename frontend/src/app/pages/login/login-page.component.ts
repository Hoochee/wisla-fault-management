import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="login-page">
      <div class="login-card">
        <div class="login-header">
          <span class="logo-icon">W</span>
          <h1>WISLA FM</h1>
          <p>Fault Management — вход в систему</p>
        </div>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <label>
            Логин
            <input type="text" formControlName="login" autocomplete="username" />
          </label>
          <label>
            Пароль
            <input type="password" formControlName="password" autocomplete="current-password" />
          </label>
          @if (error) {
            <div class="error">{{ error }}</div>
          }
          <button type="submit" [disabled]="form.invalid || loading">Войти</button>
        </form>
        <p class="hint">Dev: admin / admin</p>
      </div>
    </div>
  `,
  styles: [
    `
      .login-page {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--bg-primary);
        padding: 1rem;
      }
      .login-card {
        width: 100%;
        max-width: 400px;
        background: var(--bg-sidebar);
        border: 1px solid var(--border);
        border-radius: 8px;
        padding: 2rem;
      }
      .login-header {
        text-align: center;
        margin-bottom: 1.5rem;
      }
      .logo-icon {
        display: inline-flex;
        width: 3rem;
        height: 3rem;
        border-radius: 8px;
        background: var(--accent);
        color: #fff;
        align-items: center;
        justify-content: center;
        font-weight: 700;
        font-size: 1.25rem;
        margin-bottom: 0.75rem;
      }
      h1 { margin: 0; color: #fff; font-size: 1.5rem; }
      p { margin: 0.25rem 0 0; color: var(--text-muted); font-size: 0.875rem; }
      form { display: flex; flex-direction: column; gap: 1rem; }
      label { display: flex; flex-direction: column; gap: 0.375rem; font-size: 0.8125rem; color: var(--text-secondary); }
      input {
        padding: 0.625rem 0.75rem;
        border-radius: 6px;
        border: 1px solid var(--border);
        background: var(--bg-primary);
        color: var(--text-primary);
      }
      input:focus { outline: none; border-color: var(--accent); }
      button[type='submit'] {
        padding: 0.75rem;
        background: var(--accent);
        color: #fff;
        border: none;
        border-radius: 6px;
        font-weight: 600;
        cursor: pointer;
      }
      button:disabled { opacity: 0.5; cursor: not-allowed; }
      .error { color: #f44336; font-size: 0.8125rem; }
      .hint { margin-top: 1rem; font-size: 0.75rem; color: var(--text-muted); text-align: center; }
    `,
  ],
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  loading = false;
  error = '';

  readonly form = this.fb.nonNullable.group({
    login: ['admin', Validators.required],
    password: ['', Validators.required],
  });

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/';
        void this.router.navigateByUrl(returnUrl);
      },
      error: () => {
        this.error = 'Неверный логин или пароль';
        this.loading = false;
      },
      complete: () => (this.loading = false),
    });
  }
}

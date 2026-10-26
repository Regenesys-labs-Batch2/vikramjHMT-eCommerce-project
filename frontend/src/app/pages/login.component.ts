import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/errors';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="card narrow stack">
      <h1>Log in</h1>
      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
        <div>
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" autocomplete="username" />
        </div>
        <div>
          <label for="password">Password</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        </div>
        <button type="submit" [disabled]="form.invalid || busy()">Log in</button>
      </form>
      <p class="muted">No account? <a routerLink="/register">Register</a></p>
    </div>
  `,
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => {
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        // Only follow same-app relative paths to avoid open redirects.
        const target = returnUrl && returnUrl.startsWith('/') && !returnUrl.startsWith('//') ? returnUrl : '/products';
        this.router.navigateByUrl(target);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

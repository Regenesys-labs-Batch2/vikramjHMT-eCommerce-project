import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/errors';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="card narrow stack">
      <h1>Create account</h1>
      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }
      <form [formGroup]="form" (ngSubmit)="submit()" class="stack">
        <div>
          <label for="fullName">Full name</label>
          <input id="fullName" formControlName="fullName" maxlength="100" />
        </div>
        <div>
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" maxlength="254" autocomplete="username" />
        </div>
        <div>
          <label for="password">Password (min 8 characters)</label>
          <input id="password" type="password" formControlName="password" autocomplete="new-password" />
          @if (form.controls.password.touched && form.controls.password.invalid) {
            <span class="error">Password must be 8 to 72 characters.</span>
          }
        </div>
        <button type="submit" [disabled]="form.invalid || busy()">Register</button>
      </form>
      <p class="muted">Already registered? <a routerLink="/login">Log in</a></p>
    </div>
  `,
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    const { email, password, fullName } = this.form.getRawValue();
    this.auth.register({ email, password, fullName }).subscribe({
      next: () => this.router.navigate(['/login']),
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

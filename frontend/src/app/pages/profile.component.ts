import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { UserApi } from '../core/api.services';
import { describeError } from '../core/errors';
import { UserResponse } from '../core/models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="card narrow stack">
      <h1>My profile</h1>
      @if (user(); as u) {
        <div class="muted">{{ u.email }} · {{ u.roles.join(', ') }}</div>
      }
      @if (error()) {
        <div class="alert error" role="alert">{{ error() }}</div>
      }
      @if (message()) {
        <div class="alert success" role="status">{{ message() }}</div>
      }
      <form [formGroup]="form" (ngSubmit)="save()" class="stack">
        <div>
          <label for="fullName">Full name</label>
          <input id="fullName" formControlName="fullName" maxlength="100" />
        </div>
        <div>
          <label for="phone">Phone</label>
          <input id="phone" formControlName="phone" maxlength="20" />
          @if (form.controls.phone.invalid) {
            <span class="error">Use only digits, spaces, + or -.</span>
          }
        </div>
        <div>
          <label for="addressLine1">Address</label>
          <input id="addressLine1" formControlName="addressLine1" maxlength="200" />
        </div>
        <div>
          <label for="city">City</label>
          <input id="city" formControlName="city" maxlength="100" />
        </div>
        <div>
          <label for="postcode">Postcode</label>
          <input id="postcode" formControlName="postcode" maxlength="20" />
        </div>
        <button type="submit" [disabled]="form.invalid || busy()">Save</button>
      </form>
    </div>
  `,
})
export class ProfileComponent implements OnInit {
  private readonly api = inject(UserApi);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', Validators.pattern(/^[0-9+\- ]{0,20}$/)],
    addressLine1: ['', Validators.maxLength(200)],
    city: ['', Validators.maxLength(100)],
    postcode: ['', Validators.maxLength(20)],
  });
  protected readonly user = signal<UserResponse | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.api.me().subscribe({
      next: (u) => this.apply(u),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api.updateMe(this.form.getRawValue()).subscribe({
      next: (u) => {
        this.apply(u);
        this.message.set('Profile saved.');
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }

  private apply(u: UserResponse): void {
    this.user.set(u);
    this.form.setValue({
      fullName: u.profile?.fullName ?? '',
      phone: u.profile?.phone ?? '',
      addressLine1: u.profile?.addressLine1 ?? '',
      city: u.profile?.city ?? '',
      postcode: u.profile?.postcode ?? '',
    });
  }
}

import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CartApi } from '../../core/api.services';
import { describeError } from '../../core/errors';
import { Discount } from '../../core/models';

@Component({
  selector: 'app-admin-discounts',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, DatePipe],
  template: `
    <h1>Discount rules</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (message()) {
      <div class="alert success" role="status">{{ message() }}</div>
    }

    <form class="card row" [formGroup]="form" (ngSubmit)="create()">
      <div style="flex:1 1 140px">
        <label for="code">Code</label>
        <input id="code" formControlName="code" maxlength="50" />
      </div>
      <div style="flex:1 1 120px">
        <label for="type">Type</label>
        <select id="type" formControlName="type">
          <option value="PERCENT">Percent</option>
          <option value="FIXED">Fixed amount</option>
        </select>
      </div>
      <div style="flex:1 1 100px">
        <label for="value">Value</label>
        <input id="value" type="number" step="0.01" min="0.01" formControlName="value" />
      </div>
      <div style="flex:1 1 140px">
        <label for="min">Minimum order total</label>
        <input id="min" type="number" step="0.01" min="0" formControlName="minimumOrderTotal" />
      </div>
      <label style="display:flex; gap:.4rem; align-items:center; margin:0; align-self:end">
        <input type="checkbox" formControlName="active" style="width:auto" /> Active
      </label>
      <button type="submit" style="align-self:end" [disabled]="form.invalid || busy()">Create</button>
    </form>
    @if (form.controls.type.value === 'PERCENT' && (form.controls.value.value ?? 0) > 100) {
      <p class="error">A percent discount cannot exceed 100.</p>
    }

    <table style="margin-top:1rem">
      <thead>
        <tr><th>Code</th><th>Type</th><th class="num">Value</th><th class="num">Min order</th><th>Active</th><th>Valid to</th></tr>
      </thead>
      <tbody>
        @for (d of discounts(); track d.id) {
          <tr>
            <td>{{ d.code }}</td>
            <td>{{ d.type }}</td>
            <td class="num">{{ d.value }}</td>
            <td class="num">{{ d.minimumOrderTotal | currency: 'INR' }}</td>
            <td>{{ d.active ? 'Yes' : 'No' }}</td>
            <td>{{ d.validTo ? (d.validTo | date: 'mediumDate') : '-' }}</td>
          </tr>
        } @empty {
          <tr><td colspan="6" class="muted">No discount rules.</td></tr>
        }
      </tbody>
    </table>
  `,
})
export class AdminDiscountsComponent implements OnInit {
  private readonly api = inject(CartApi);

  protected readonly form = inject(FormBuilder).group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
    type: ['PERCENT' as 'PERCENT' | 'FIXED'],
    value: [null as number | null, [Validators.required, Validators.min(0.01)]],
    minimumOrderTotal: [0 as number | null, [Validators.required, Validators.min(0)]],
    active: [true],
  });
  protected readonly discounts = signal<Discount[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.load();
  }

  protected create(): void {
    const v = this.form.getRawValue();
    if (this.form.invalid || v.value == null || (v.type === 'PERCENT' && v.value > 100)) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    this.api
      .createDiscount({
        code: (v.code ?? '').trim().toUpperCase(),
        type: v.type ?? 'PERCENT',
        value: v.value,
        minimumOrderTotal: v.minimumOrderTotal ?? 0,
        active: v.active ?? true,
      })
      .subscribe({
        next: () => {
          this.form.reset({ code: '', type: 'PERCENT', value: null, minimumOrderTotal: 0, active: true });
          this.message.set('Discount rule created.');
          this.busy.set(false);
          this.load();
        },
        error: (err) => {
          this.error.set(describeError(err));
          this.busy.set(false);
        },
      });
  }

  private load(): void {
    this.api.discounts().subscribe({
      next: (d) => this.discounts.set(d),
      error: (err) => this.error.set(describeError(err)),
    });
  }
}

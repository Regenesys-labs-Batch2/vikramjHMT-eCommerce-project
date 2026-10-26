import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../../core/api.services';
import { describeError } from '../../core/errors';
import { Order, Page } from '../../core/models';
import { PagerComponent } from '../../shared/pager.component';

@Component({
  selector: 'app-admin-orders',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, PagerComponent],
  template: `
    <h1>Order lookup</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    <form class="card row" [formGroup]="form" (ngSubmit)="byCustomer(0)">
      <div style="flex:1 1 280px">
        <label for="customerId">Customer id</label>
        <input id="customerId" formControlName="customerId" />
      </div>
      <button type="submit" style="align-self:end" [disabled]="form.invalid">Find orders</button>
    </form>
    <form class="card row" [formGroup]="orderForm" (ngSubmit)="open()" style="margin-top:.75rem">
      <div style="flex:1 1 280px">
        <label for="orderId">Order id</label>
        <input id="orderId" formControlName="orderId" />
      </div>
      <button type="submit" class="secondary" style="align-self:end" [disabled]="orderForm.invalid">Open order</button>
    </form>

    @if (result(); as r) {
      <table style="margin-top:1rem">
        <thead><tr><th>Order</th><th>Placed</th><th>Status</th><th class="num">Total</th></tr></thead>
        <tbody>
          @for (o of r.content; track o.id) {
            <tr>
              <td><a [routerLink]="['/orders', o.id]">{{ o.id }}</a></td>
              <td>{{ o.createdAt | date: 'medium' }}</td>
              <td><span [class]="'badge ' + o.status">{{ o.status }}</span></td>
              <td class="num">{{ o.totalAmount | currency: o.currency }}</td>
            </tr>
          } @empty {
            <tr><td colspan="4" class="muted">No orders for this customer.</td></tr>
          }
        </tbody>
      </table>
      <app-pager [page]="r.page" [totalPages]="r.totalPages" (pageChange)="byCustomer($event)" />
    }
  `,
})
export class AdminOrdersComponent {
  private readonly api = inject(OrderApi);
  private readonly fb = inject(FormBuilder);

  protected readonly form = this.fb.nonNullable.group({ customerId: ['', Validators.required] });
  protected readonly orderForm = this.fb.nonNullable.group({ orderId: ['', Validators.required] });
  protected readonly result = signal<Page<Order> | null>(null);
  protected readonly error = signal<string | null>(null);

  protected byCustomer(page: number): void {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.api.byCustomer(this.form.getRawValue().customerId.trim(), page, 10).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected open(): void {
    if (this.orderForm.invalid) {
      return;
    }
    this.error.set(null);
    this.api.get(this.orderForm.getRawValue().orderId.trim()).subscribe({
      next: (o) => this.result.set({ content: [o], page: 0, size: 1, totalElements: 1, totalPages: 1 }),
      error: (err) => this.error.set(describeError(err)),
    });
  }
}

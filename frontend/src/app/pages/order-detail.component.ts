import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderApi, PaymentApi } from '../core/api.services';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/errors';
import { Order, Payment } from '../core/models';

const CANCELLABLE = ['CONFIRMED', 'PAYMENT_FAILED'];
const RECOVERABLE = ['PAYMENT_PENDING', 'COMPENSATION_PENDING', 'STOCK_RESERVED'];

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [RouterLink, CurrencyPipe, DatePipe],
  template: `
    <p><a [routerLink]="auth.isAdmin() ? '/admin/orders' : '/orders'">&larr; Back to orders</a></p>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (message()) {
      <div class="alert success" role="status">{{ message() }}</div>
    }

    @if (order(); as o) {
      <div class="card stack">
        <div class="row between">
          <h1 style="margin:0">Order {{ o.id.substring(0, 8) }}</h1>
          <span class="badge" [class]="'badge ' + o.status">{{ o.status }}</span>
        </div>
        <div class="muted">Placed {{ o.createdAt | date: 'medium' }}</div>
        @if (o.failureReason) {
          <div class="alert error">Reason: {{ o.failureReason }}</div>
        }

        <table>
          <thead>
            <tr><th>Product</th><th class="num">Unit price</th><th class="num">Qty</th><th class="num">Line total</th></tr>
          </thead>
          <tbody>
            @for (i of o.items; track i.productId) {
              <tr>
                <td>{{ i.productName }} <span class="muted">{{ i.sku }}</span></td>
                <td class="num">{{ i.unitPrice | currency: o.currency }}</td>
                <td class="num">{{ i.quantity }}</td>
                <td class="num">{{ i.lineTotal | currency: o.currency }}</td>
              </tr>
            }
            <tr>
              <td colspan="3" class="num">Subtotal</td>
              <td class="num">{{ o.subtotal | currency: o.currency }}</td>
            </tr>
            <tr>
              <td colspan="3" class="num">Discount</td>
              <td class="num">-{{ o.discountAmount | currency: o.currency }}</td>
            </tr>
            <tr class="total-row">
              <td colspan="3" class="num">Total</td>
              <td class="num">{{ o.totalAmount | currency: o.currency }}</td>
            </tr>
          </tbody>
        </table>

        <div class="row">
          <button type="button" class="secondary" [disabled]="busy()" (click)="load()">Refresh</button>
          @if (canCancel()) {
            <button type="button" class="danger" [disabled]="busy()" (click)="cancel()">Cancel order</button>
          }
          @if (canRecover()) {
            <button type="button" [disabled]="busy()" (click)="recover()">Recover</button>
          }
        </div>
      </div>

      <h2>Status history</h2>
      <table>
        <thead><tr><th>When</th><th>From</th><th>To</th><th>Reason</th></tr></thead>
        <tbody>
          @for (h of o.statusHistory; track $index) {
            <tr>
              <td>{{ h.changedAt | date: 'medium' }}</td>
              <td>{{ h.fromStatus ?? '-' }}</td>
              <td>{{ h.toStatus }}</td>
              <td>{{ h.reason ?? '' }}</td>
            </tr>
          }
        </tbody>
      </table>

      @if (payments().length > 0) {
        <h2>Payment</h2>
        @for (p of payments(); track p.id) {
          <div class="card stack">
            <div class="row between">
              <span>{{ p.amount | currency: p.currency }}</span>
              <span class="badge" [class]="'badge ' + p.status">{{ p.status }}</span>
            </div>
            @if (p.failureReason) {
              <div class="muted">Failure: {{ p.failureReason }}</div>
            }
            <div class="muted">
              @for (a of p.attempts; track a.attemptNumber) {
                <div>Attempt {{ a.attemptNumber }}: {{ a.outcome }} ({{ a.attemptedAt | date: 'mediumTime' }})</div>
              }
            </div>
          </div>
        }
      }
    }
  `,
})
export class OrderDetailComponent implements OnInit {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly orders = inject(OrderApi);
  private readonly paymentApi = inject(PaymentApi);

  protected readonly order = signal<Order | null>(null);
  protected readonly payments = signal<Payment[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly canCancel = computed(() => CANCELLABLE.includes(this.order()?.status ?? ''));
  protected readonly canRecover = computed(
    () => this.auth.isAdmin() && RECOVERABLE.includes(this.order()?.status ?? ''),
  );

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.error.set(null);
    this.orders.get(this.id()).subscribe({
      next: (o) => {
        this.order.set(o);
        // Payment details are supplementary; the order view must not fail without them.
        this.paymentApi.byOrder(o.id).subscribe({ next: (p) => this.payments.set(p), error: () => undefined });
      },
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected cancel(): void {
    if (!confirm('Cancel this order?')) {
      return;
    }
    this.act(this.orders.cancel(this.id()), 'Order cancelled.');
  }

  protected recover(): void {
    this.act(this.orders.recover(this.id()), 'Recovery step executed.');
  }

  private act(call: ReturnType<OrderApi['cancel']>, ok: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    call.subscribe({
      next: (o) => {
        this.order.set(o);
        this.message.set(ok);
        this.busy.set(false);
        this.paymentApi.byOrder(o.id).subscribe({ next: (p) => this.payments.set(p), error: () => undefined });
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

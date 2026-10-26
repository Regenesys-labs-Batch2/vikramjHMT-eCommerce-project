import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { OrderApi } from '../core/api.services';
import { CartStore } from '../core/cart.store';
import { describeError, errorStatus } from '../core/errors';
import { SimulatedOutcome } from '../core/models';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [FormsModule, RouterLink, CurrencyPipe],
  template: `
    <h1>Checkout</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }

    @if (cart.cart(); as c) {
      @if (c.items.length === 0) {
        <div class="card">Your cart is empty. <a routerLink="/products">Browse products</a></div>
      } @else {
        <table>
          <thead>
            <tr><th>Product</th><th class="num">Qty</th><th class="num">Line total</th></tr>
          </thead>
          <tbody>
            @for (i of c.items; track i.id) {
              <tr>
                <td>{{ i.productName }}</td>
                <td class="num">{{ i.quantity }}</td>
                <td class="num">{{ i.lineTotal | currency: c.currency }}</td>
              </tr>
            }
            <tr>
              <td colspan="2" class="num">Subtotal</td>
              <td class="num">{{ c.subtotal | currency: c.currency }}</td>
            </tr>
            @if (c.discountCode) {
              <tr>
                <td colspan="2" class="num">Discount ({{ c.discountCode }})</td>
                <td class="num">-{{ c.discountAmount | currency: c.currency }}</td>
              </tr>
            }
            <tr class="total-row">
              <td colspan="2" class="num">Total</td>
              <td class="num">{{ c.total | currency: c.currency }}</td>
            </tr>
          </tbody>
        </table>
        <p class="muted">The final price is re-checked by the server when the order is placed.</p>

        <div class="card stack" style="margin-top:1rem">
          <div>
            <label for="outcome">Payment simulation (demo only, no real payment is made)</label>
            <select id="outcome" [ngModel]="outcome()" (ngModelChange)="outcome.set($event)">
              <option value="">Automatic</option>
              <option value="SUCCESS">Force success</option>
              <option value="DECLINED">Force decline</option>
              <option value="TIMEOUT">Force timeout</option>
            </select>
          </div>
          <div class="row" style="justify-content:flex-end">
            <a routerLink="/cart">Back to cart</a>
            <button type="button" [disabled]="busy()" (click)="placeOrder()">
              {{ busy() ? 'Placing order...' : 'Place order' }}
            </button>
          </div>
        </div>
      }
    } @else if (!error()) {
      <p class="muted">Loading...</p>
    }
  `,
})
export class CheckoutComponent implements OnInit {
  protected readonly cart = inject(CartStore);
  private readonly orders = inject(OrderApi);
  private readonly router = inject(Router);

  protected readonly outcome = signal<SimulatedOutcome | ''>('');
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  // The same key is reused for retries after network or 503 failures so no duplicate order is created.
  private idempotencyKey = crypto.randomUUID();

  ngOnInit(): void {
    this.cart.refresh().subscribe({ error: (err) => this.error.set(describeError(err)) });
  }

  protected placeOrder(): void {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.orders.create(this.idempotencyKey, this.outcome() || undefined).subscribe({
      next: (order) => {
        this.idempotencyKey = crypto.randomUUID();
        this.cart.refresh().subscribe({ error: () => this.cart.clear() });
        this.router.navigate(['/orders', order.id]);
      },
      error: (err) => {
        const status = errorStatus(err);
        if (status !== 0 && status !== 503) {
          this.idempotencyKey = crypto.randomUUID();
        }
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

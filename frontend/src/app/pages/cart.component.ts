import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { CartStore } from '../core/cart.store';
import { describeError } from '../core/errors';
import { CartItem } from '../core/models';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe],
  template: `
    <h1>Your cart</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (message()) {
      <div class="alert success" role="status">{{ message() }}</div>
    }

    @if (store.cart(); as cart) {
      @if (cart.items.length === 0) {
        <div class="card">Your cart is empty. <a routerLink="/products">Browse products</a></div>
      } @else {
        <table>
          <thead>
            <tr>
              <th>Product</th>
              <th class="num">Unit price</th>
              <th>Quantity</th>
              <th class="num">Line total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            @for (item of cart.items; track item.id) {
              <tr>
                <td>
                  <a [routerLink]="['/products', item.productId]">{{ item.productName }}</a>
                  <div class="muted">{{ item.sku }}</div>
                </td>
                <td class="num">{{ item.unitPrice | currency: cart.currency }}</td>
                <td style="width:120px">
                  <input
                    type="number"
                    min="1"
                    max="100"
                    [value]="item.quantity"
                    [attr.aria-label]="'Quantity for ' + item.productName"
                    (change)="changeQuantity(item, $any($event.target).value)"
                  />
                </td>
                <td class="num">{{ item.lineTotal | currency: cart.currency }}</td>
                <td><button type="button" class="link" (click)="remove(item)">Remove</button></td>
              </tr>
            }
            <tr>
              <td colspan="3" class="num">Subtotal</td>
              <td class="num">{{ cart.subtotal | currency: cart.currency }}</td>
              <td></td>
            </tr>
            @if (cart.discountCode) {
              <tr>
                <td colspan="3" class="num">Discount ({{ cart.discountCode }})</td>
                <td class="num">-{{ cart.discountAmount | currency: cart.currency }}</td>
                <td></td>
              </tr>
            }
            <tr class="total-row">
              <td colspan="3" class="num">Total</td>
              <td class="num">{{ cart.total | currency: cart.currency }}</td>
              <td></td>
            </tr>
          </tbody>
        </table>

        <form [formGroup]="discountForm" (ngSubmit)="applyDiscount()" class="row card" style="margin-top:1rem">
          <div style="flex:1 1 200px">
            <label for="code">Discount code</label>
            <input id="code" formControlName="code" maxlength="50" placeholder="e.g. WELCOME10" />
          </div>
          <button type="submit" class="secondary" style="align-self:end" [disabled]="discountForm.invalid || busy()">
            Apply
          </button>
        </form>

        <div class="row" style="margin-top:1rem; justify-content:flex-end">
          <a class="btn" routerLink="/checkout">Proceed to checkout</a>
        </div>
      }
    } @else if (!error()) {
      <p class="muted">Loading...</p>
    }
  `,
})
export class CartComponent implements OnInit {
  protected readonly store = inject(CartStore);

  protected readonly discountForm = inject(FormBuilder).nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(50)]],
  });
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.store.refresh().subscribe({ error: (err) => this.error.set(describeError(err)) });
  }

  protected changeQuantity(item: CartItem, raw: string): void {
    const quantity = Number(raw);
    this.reset();
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 100) {
      this.error.set('Quantity must be a whole number between 1 and 100.');
      this.store.refresh().subscribe();
      return;
    }
    this.run(this.store.update(item.id, quantity));
  }

  protected remove(item: CartItem): void {
    this.reset();
    this.run(this.store.remove(item.id));
  }

  protected applyDiscount(): void {
    if (this.discountForm.invalid) {
      return;
    }
    this.reset();
    const code = this.discountForm.getRawValue().code.trim().toUpperCase();
    this.run(this.store.applyDiscount(code), 'Discount applied.');
  }

  private reset(): void {
    this.error.set(null);
    this.message.set(null);
  }

  private run(call: Observable<unknown>, ok?: string): void {
    this.busy.set(true);
    call.subscribe({
      next: () => {
        if (ok) {
          this.message.set(ok);
        }
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

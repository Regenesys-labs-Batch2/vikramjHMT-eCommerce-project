import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogApi, InventoryApi } from '../core/api.services';
import { AuthService } from '../core/auth.service';
import { CartStore } from '../core/cart.store';
import { describeError } from '../core/errors';
import { Product, Stock } from '../core/models';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe],
  template: `
    <p><a routerLink="/products">&larr; Back to products</a></p>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (product(); as p) {
      <div class="card stack">
        <h1>{{ p.name }}</h1>
        <div class="muted">{{ p.categoryName }} · SKU {{ p.sku }}</div>
        <p>{{ p.description }}</p>
        <div class="price">{{ p.price.amount | currency: p.price.currency }}</div>

        @if (auth.isLoggedIn()) {
          @if (stock(); as s) {
            <div>
              @if (s.availableQuantity > 0) {
                <span class="badge CONFIRMED">{{ s.availableQuantity }} in stock</span>
              } @else {
                <span class="badge CANCELLED">Out of stock</span>
              }
            </div>
          }
        }

        @if (!p.active) {
          <div class="alert info">This product is currently unavailable.</div>
        } @else if (auth.isCustomer()) {
          @if (message()) {
            <div class="alert success" role="status">
              {{ message() }} <a routerLink="/cart">View cart</a>
            </div>
          }
          <form [formGroup]="form" (ngSubmit)="addToCart(p)" class="row">
            <div style="width:100px">
              <label for="qty">Quantity</label>
              <input id="qty" type="number" min="1" max="100" formControlName="quantity" />
            </div>
            <button type="submit" style="align-self:end" [disabled]="form.invalid || busy()">Add to cart</button>
          </form>
          @if (form.controls.quantity.invalid) {
            <span class="error">Quantity must be between 1 and 100.</span>
          }
        } @else if (!auth.isLoggedIn()) {
          <p><a routerLink="/login" [queryParams]="{ returnUrl: '/products/' + p.id }">Log in</a> to add this item to your cart.</p>
        }
      </div>
    }
  `,
})
export class ProductDetailComponent implements OnInit {
  readonly id = input.required<string>();

  protected readonly auth = inject(AuthService);
  private readonly catalog = inject(CatalogApi);
  private readonly inventory = inject(InventoryApi);
  private readonly cart = inject(CartStore);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(100)]],
  });
  protected readonly product = signal<Product | null>(null);
  protected readonly stock = signal<Stock | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    this.catalog.product(this.id()).subscribe({
      next: (p) => this.product.set(p),
      error: (err) => this.error.set(describeError(err)),
    });
    if (this.auth.isLoggedIn()) {
      // Stock is informational; a missing row must not block the page.
      this.inventory.stock(this.id()).subscribe({ next: (s) => this.stock.set(s), error: () => undefined });
    }
  }

  protected addToCart(p: Product): void {
    if (this.form.invalid) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
    this.cart.add(p.id, this.form.getRawValue().quantity).subscribe({
      next: () => {
        this.message.set('Added to cart.');
        this.busy.set(false);
      },
      error: (err) => {
        this.error.set(describeError(err));
        this.busy.set(false);
      },
    });
  }
}

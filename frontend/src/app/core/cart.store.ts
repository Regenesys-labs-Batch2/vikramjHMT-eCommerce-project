import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, switchMap, tap } from 'rxjs';
import { CartApi } from './api.services';
import { Cart } from './models';

// Holds the server-computed cart so the header badge and cart page stay in sync.
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly api = inject(CartApi);
  private readonly state = signal<Cart | null>(null);

  readonly cart = this.state.asReadonly();
  readonly count = computed(() => this.state()?.items.reduce((n, i) => n + i.quantity, 0) ?? 0);

  refresh(): Observable<Cart> {
    return this.api.current().pipe(tap((c) => this.state.set(c)));
  }

  add(productId: string, quantity: number): Observable<Cart> {
    return this.api.addItem(productId, quantity).pipe(tap((c) => this.state.set(c)));
  }

  update(itemId: string, quantity: number): Observable<Cart> {
    return this.api.updateItem(itemId, quantity).pipe(tap((c) => this.state.set(c)));
  }

  applyDiscount(code: string): Observable<Cart> {
    return this.api.applyDiscount(code).pipe(tap((c) => this.state.set(c)));
  }

  remove(itemId: string): Observable<Cart> {
    return this.api.removeItem(itemId).pipe(switchMap(() => this.refresh()));
  }

  clear(): void {
    this.state.set(null);
  }
}

import { Component, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';
import { CartStore } from './core/cart.store';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styles: [
    `
      header { background: #1f2430; color: #fff; }
      nav { max-width: 1100px; margin: 0 auto; padding: .75rem 1rem; display: flex; gap: 1rem; align-items: center; flex-wrap: wrap; }
      nav a { color: #d1d5db; }
      nav a.active { color: #fff; font-weight: 600; }
      .brand { font-weight: 700; color: #fff; font-size: 1.1rem; margin-right: auto; }
      .count { background: #4f46e5; border-radius: 999px; padding: 0 .45rem; font-size: .75rem; margin-left: .25rem; }
    `,
  ],
  template: `
    <header>
      <nav>
        <a class="brand" routerLink="/products">Happiest Minds Store</a>
        <a routerLink="/products" routerLinkActive="active">Products</a>
        @if (auth.isCustomer()) {
          <a routerLink="/cart" routerLinkActive="active">
            Cart @if (cart.count() > 0) { <span class="count">{{ cart.count() }}</span> }
          </a>
          <a routerLink="/orders" routerLinkActive="active">Orders</a>
        }
        @if (auth.isAdmin()) {
          <a routerLink="/admin/catalog" routerLinkActive="active">Catalog admin</a>
          <a routerLink="/admin/discounts" routerLinkActive="active">Discounts</a>
          <a routerLink="/admin/orders" routerLinkActive="active">Orders admin</a>
        }
        @if (auth.isLoggedIn()) {
          <a routerLink="/profile" routerLinkActive="active">Profile</a>
          <button class="secondary" type="button" (click)="logout()">Log out</button>
        } @else {
          <a routerLink="/login" routerLinkActive="active">Log in</a>
          <a routerLink="/register" routerLinkActive="active">Register</a>
        }
      </nav>
    </header>
    <main class="container">
      <router-outlet />
    </main>
  `,
})
export class AppComponent {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  constructor() {
    effect(
      () => {
        if (this.auth.isCustomer()) {
          this.cart.refresh().subscribe({ error: () => this.cart.clear() });
        } else {
          this.cart.clear();
        }
      },
      { allowSignalWrites: true },
    );
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}

import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderApi } from '../core/api.services';
import { AuthService } from '../core/auth.service';
import { describeError } from '../core/errors';
import { Order, Page } from '../core/models';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-orders',
  standalone: true,
  imports: [RouterLink, CurrencyPipe, DatePipe, PagerComponent],
  template: `
    <h1>My orders</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (result(); as r) {
      @if (r.content.length === 0) {
        <div class="card">You have no orders yet. <a routerLink="/products">Start shopping</a></div>
      } @else {
        <table>
          <thead>
            <tr><th>Order</th><th>Placed</th><th>Status</th><th class="num">Total</th></tr>
          </thead>
          <tbody>
            @for (o of r.content; track o.id) {
              <tr>
                <td><a [routerLink]="['/orders', o.id]">{{ o.id.substring(0, 8) }}</a></td>
                <td>{{ o.createdAt | date: 'medium' }}</td>
                <td><span class="badge" [class]="'badge ' + o.status">{{ o.status }}</span></td>
                <td class="num">{{ o.totalAmount | currency: o.currency }}</td>
              </tr>
            }
          </tbody>
        </table>
        <app-pager [page]="r.page" [totalPages]="r.totalPages" (pageChange)="load($event)" />
      }
    } @else if (!error()) {
      <p class="muted">Loading...</p>
    }
  `,
})
export class OrdersComponent implements OnInit {
  private readonly api = inject(OrderApi);
  private readonly auth = inject(AuthService);

  protected readonly result = signal<Page<Order> | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    const userId = this.auth.userId();
    if (!userId) {
      return;
    }
    this.error.set(null);
    this.api.byCustomer(userId, page, 10).subscribe({
      next: (r) => this.result.set(r),
      error: (err) => this.error.set(describeError(err)),
    });
  }
}

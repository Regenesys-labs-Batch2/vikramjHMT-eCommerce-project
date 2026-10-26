import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../core/api.services';
import { describeError } from '../core/errors';
import { Category, Page, Product } from '../core/models';
import { PagerComponent } from '../shared/pager.component';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, CurrencyPipe, PagerComponent],
  template: `
    <h1>Products</h1>
    <form class="card row" [formGroup]="filters" (ngSubmit)="load(0)">
      <div style="flex:2 1 200px">
        <label for="q">Search</label>
        <input id="q" formControlName="q" placeholder="Name or SKU" />
      </div>
      <div style="flex:1 1 160px">
        <label for="categoryId">Category</label>
        <select id="categoryId" formControlName="categoryId">
          <option value="">All</option>
          @for (c of categories(); track c.id) {
            <option [value]="c.id">{{ c.name }}</option>
          }
        </select>
      </div>
      <div style="flex:1 1 100px">
        <label for="minPrice">Min price</label>
        <input id="minPrice" type="number" min="0" formControlName="minPrice" />
      </div>
      <div style="flex:1 1 100px">
        <label for="maxPrice">Max price</label>
        <input id="maxPrice" type="number" min="0" formControlName="maxPrice" />
      </div>
      <div style="flex:1 1 140px">
        <label for="sort">Sort</label>
        <select id="sort" formControlName="sort">
          <option value="name,asc">Name A-Z</option>
          <option value="name,desc">Name Z-A</option>
          <option value="createdAt,desc">Newest</option>
        </select>
      </div>
      <button type="submit" style="align-self:end" [disabled]="loading()">Search</button>
    </form>

    @if (priceRangeInvalid()) {
      <p class="error">Min price cannot be greater than max price.</p>
    }
    @if (error()) {
      <div class="alert error" role="alert" style="margin-top:1rem">{{ error() }}</div>
    }

    @if (result(); as r) {
      <p class="muted">{{ r.totalElements }} product(s)</p>
      @if (r.content.length === 0) {
        <div class="card">No products match your search.</div>
      }
      <div class="grid">
        @for (p of r.content; track p.id) {
          <div class="card stack">
            <div class="muted">{{ p.categoryName }} · {{ p.sku }}</div>
            <a [routerLink]="['/products', p.id]"><strong>{{ p.name }}</strong></a>
            <div class="price">{{ p.price.amount | currency: p.price.currency }}</div>
          </div>
        }
      </div>
      <app-pager [page]="r.page" [totalPages]="r.totalPages" (pageChange)="load($event)" />
    }
  `,
})
export class ProductListComponent implements OnInit {
  private readonly api = inject(CatalogApi);

  protected readonly filters = inject(FormBuilder).group({
    q: [''],
    categoryId: [''],
    minPrice: [null as number | null],
    maxPrice: [null as number | null],
    sort: ['name,asc'],
  });
  protected readonly categories = signal<Category[]>([]);
  protected readonly result = signal<Page<Product> | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.api.categories().subscribe({
      next: (c) => this.categories.set(c),
      error: (err) => this.error.set(describeError(err)),
    });
    this.load(0);
  }

  protected priceRangeInvalid(): boolean {
    const { minPrice, maxPrice } = this.filters.value;
    return minPrice != null && maxPrice != null && minPrice > maxPrice;
  }

  protected load(page: number): void {
    if (this.priceRangeInvalid()) {
      return;
    }
    const f = this.filters.value;
    this.loading.set(true);
    this.error.set(null);
    this.api
      .products({
        q: f.q?.trim() || undefined,
        categoryId: f.categoryId || undefined,
        minPrice: f.minPrice ?? undefined,
        maxPrice: f.maxPrice ?? undefined,
        active: true,
        sort: f.sort ?? undefined,
        page,
        size: 12,
      })
      .subscribe({
        next: (r) => {
          this.result.set(r);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(describeError(err));
          this.loading.set(false);
        },
      });
  }
}

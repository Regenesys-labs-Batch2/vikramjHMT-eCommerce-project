import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { CatalogApi, InventoryApi } from '../../core/api.services';
import { describeError } from '../../core/errors';
import { Category, Page, Product, Stock } from '../../core/models';
import { PagerComponent } from '../../shared/pager.component';

function maxTwoDecimals(control: AbstractControl): ValidationErrors | null {
  const v = control.value as number | null;
  return v == null || Math.round(v * 100) / 100 === v ? null : { decimals: true };
}

@Component({
  selector: 'app-admin-catalog',
  standalone: true,
  imports: [ReactiveFormsModule, CurrencyPipe, PagerComponent],
  template: `
    <h1>Catalog administration</h1>
    @if (error()) {
      <div class="alert error" role="alert">{{ error() }}</div>
    }
    @if (message()) {
      <div class="alert success" role="status">{{ message() }}</div>
    }

    <h2>Categories</h2>
    <div class="card stack">
      <div class="muted">
        @for (c of categories(); track c.id) {
          <span class="badge" style="margin-right:.4rem">{{ c.name }}</span>
        } @empty {
          No categories yet.
        }
      </div>
      <form [formGroup]="categoryForm" (ngSubmit)="addCategory()" class="row">
        <div style="flex:1 1 180px">
          <label for="catName">Name</label>
          <input id="catName" formControlName="name" maxlength="100" />
        </div>
        <div style="flex:2 1 240px">
          <label for="catDesc">Description</label>
          <input id="catDesc" formControlName="description" maxlength="500" />
        </div>
        <button type="submit" style="align-self:end" [disabled]="categoryForm.invalid">Add category</button>
      </form>
    </div>

    <h2>{{ editing() ? 'Edit product ' + editing()!.sku : 'New product' }}</h2>
    <form class="card" [formGroup]="productForm" (ngSubmit)="saveProduct()">
      <div class="row">
        <div style="flex:1 1 120px">
          <label for="sku">SKU (AAA-999)</label>
          <input id="sku" formControlName="sku" />
          @if (productForm.controls.sku.touched && productForm.controls.sku.invalid) {
            <span class="error">SKU must look like LAP-001.</span>
          }
        </div>
        <div style="flex:2 1 220px">
          <label for="pname">Name</label>
          <input id="pname" formControlName="name" maxlength="200" />
        </div>
        <div style="flex:1 1 160px">
          <label for="pcat">Category</label>
          <select id="pcat" formControlName="categoryId">
            <option value="">Select...</option>
            @for (c of categories(); track c.id) {
              <option [value]="c.id">{{ c.name }}</option>
            }
          </select>
        </div>
        <div style="flex:1 1 120px">
          <label for="price">Price (INR)</label>
          <input id="price" type="number" step="0.01" min="0.01" formControlName="amount" />
          @if (productForm.controls.amount.touched && productForm.controls.amount.invalid) {
            <span class="error">Positive amount, up to 2 decimals.</span>
          }
        </div>
      </div>
      <div style="margin-top:.75rem">
        <label for="pdesc">Description</label>
        <textarea id="pdesc" rows="2" maxlength="2000" formControlName="description"></textarea>
      </div>
      <div class="row" style="margin-top:.75rem">
        <label style="display:flex; gap:.4rem; align-items:center; margin:0">
          <input type="checkbox" formControlName="active" style="width:auto" /> Active
        </label>
        <button type="submit" [disabled]="productForm.invalid || busy()">
          {{ editing() ? 'Update product' : 'Create product' }}
        </button>
        @if (editing()) {
          <button type="button" class="secondary" (click)="cancelEdit()">Cancel edit</button>
        }
      </div>
    </form>

    <h2>Products</h2>
    @if (products(); as r) {
      <table>
        <thead>
          <tr><th>SKU</th><th>Name</th><th>Category</th><th class="num">Price</th><th>Active</th><th></th></tr>
        </thead>
        <tbody>
          @for (p of r.content; track p.id) {
            <tr>
              <td>{{ p.sku }}</td>
              <td>{{ p.name }}</td>
              <td>{{ p.categoryName }}</td>
              <td class="num">{{ p.price.amount | currency: p.price.currency }}</td>
              <td>{{ p.active ? 'Yes' : 'No' }}</td>
              <td class="row">
                <button type="button" class="link" (click)="edit(p)">Edit</button>
                <button type="button" class="link" (click)="openStock(p)">Stock</button>
              </td>
            </tr>
            @if (stockFor() === p.id) {
              <tr>
                <td colspan="6">
                  <div class="stack">
                    @if (stock(); as s) {
                      <div>Available: <strong>{{ s.availableQuantity }}</strong> · Reserved: <strong>{{ s.reservedQuantity }}</strong></div>
                    } @else {
                      <div class="muted">No stock record for this product yet.</div>
                    }
                    <form [formGroup]="stockForm" (ngSubmit)="adjust(p)" class="row">
                      <div style="width:110px">
                        <label for="delta">Change (+/-)</label>
                        <input id="delta" type="number" step="1" formControlName="delta" />
                      </div>
                      <div style="flex:2 1 200px">
                        <label for="reason">Reason</label>
                        <input id="reason" formControlName="reason" maxlength="200" />
                      </div>
                      <div style="flex:1 1 140px">
                        <label for="reference">Reference (optional)</label>
                        <input id="reference" formControlName="reference" maxlength="100" />
                      </div>
                      <button type="submit" style="align-self:end" [disabled]="stockForm.invalid || busy()">Apply</button>
                    </form>
                  </div>
                </td>
              </tr>
            }
          }
        </tbody>
      </table>
      <app-pager [page]="r.page" [totalPages]="r.totalPages" (pageChange)="loadProducts($event)" />
    }
  `,
})
export class AdminCatalogComponent implements OnInit {
  private readonly catalog = inject(CatalogApi);
  private readonly inventory = inject(InventoryApi);
  private readonly fb = inject(FormBuilder);

  protected readonly categoryForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    description: [''],
  });
  protected readonly productForm = this.fb.nonNullable.group({
    sku: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{3}-[0-9]{3}$/)]],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    description: [''],
    categoryId: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01), maxTwoDecimals]],
    active: [true],
  });
  protected readonly stockForm = this.fb.nonNullable.group({
    delta: [0, [Validators.required, Validators.pattern(/^-?[0-9]+$/)]],
    reason: ['', [Validators.required, Validators.maxLength(200)]],
    reference: [''],
  });

  protected readonly categories = signal<Category[]>([]);
  protected readonly products = signal<Page<Product> | null>(null);
  protected readonly editing = signal<Product | null>(null);
  protected readonly stockFor = signal<string | null>(null);
  protected readonly stock = signal<Stock | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly message = signal<string | null>(null);
  protected readonly busy = signal(false);

  private page = 0;

  ngOnInit(): void {
    this.loadCategories();
    this.loadProducts(0);
  }

  protected loadCategories(): void {
    this.catalog.categories().subscribe({
      next: (c) => this.categories.set(c),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected loadProducts(page: number): void {
    this.page = page;
    this.catalog.products({ page, size: 10, sort: 'sku,asc' }).subscribe({
      next: (r) => this.products.set(r),
      error: (err) => this.error.set(describeError(err)),
    });
  }

  protected addCategory(): void {
    if (this.categoryForm.invalid) {
      return;
    }
    const { name, description } = this.categoryForm.getRawValue();
    this.start();
    this.catalog.createCategory({ name: name.trim(), description: description.trim() || undefined }).subscribe({
      next: () => {
        this.categoryForm.reset({ name: '', description: '' });
        this.done('Category created.');
        this.loadCategories();
      },
      error: (err) => this.fail(err),
    });
  }

  protected edit(p: Product): void {
    this.editing.set(p);
    this.productForm.controls.sku.disable();
    this.productForm.setValue({
      sku: p.sku,
      name: p.name,
      description: p.description ?? '',
      categoryId: p.categoryId,
      amount: p.price.amount,
      active: p.active,
    });
  }

  protected cancelEdit(): void {
    this.editing.set(null);
    this.productForm.controls.sku.enable();
    this.productForm.reset({ sku: '', name: '', description: '', categoryId: '', amount: null, active: true });
  }

  protected saveProduct(): void {
    if (this.productForm.invalid) {
      return;
    }
    const v = this.productForm.getRawValue();
    const request = {
      sku: v.sku.toUpperCase(),
      name: v.name.trim(),
      description: v.description.trim() || undefined,
      categoryId: v.categoryId,
      price: { amount: v.amount as number, currency: 'INR' },
      active: v.active,
    };
    const current = this.editing();
    this.start();
    const call = current ? this.catalog.updateProduct(current.id, request) : this.catalog.createProduct(request);
    call.subscribe({
      next: () => {
        this.cancelEdit();
        this.done(current ? 'Product updated.' : 'Product created.');
        this.loadProducts(this.page);
      },
      error: (err) => this.fail(err),
    });
  }

  protected openStock(p: Product): void {
    if (this.stockFor() === p.id) {
      this.stockFor.set(null);
      return;
    }
    this.stockFor.set(p.id);
    this.stock.set(null);
    this.stockForm.reset({ delta: 0, reason: '', reference: '' });
    this.inventory.stock(p.id).subscribe({ next: (s) => this.stock.set(s), error: () => undefined });
  }

  protected adjust(p: Product): void {
    if (this.stockForm.invalid) {
      return;
    }
    const v = this.stockForm.getRawValue();
    this.start();
    this.inventory
      .adjust(p.id, { delta: Number(v.delta), reason: v.reason.trim(), reference: v.reference.trim() || undefined })
      .subscribe({
        next: (s) => {
          this.stock.set(s);
          this.done('Stock adjusted.');
        },
        error: (err) => this.fail(err),
      });
  }

  private start(): void {
    this.busy.set(true);
    this.error.set(null);
    this.message.set(null);
  }

  private done(msg: string): void {
    this.message.set(msg);
    this.busy.set(false);
  }

  private fail(err: unknown): void {
    this.error.set(describeError(err));
    this.busy.set(false);
  }
}

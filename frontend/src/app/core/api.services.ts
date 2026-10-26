import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import {
  AdjustmentRequest,
  Cart,
  Category,
  CategoryRequest,
  Discount,
  DiscountRequest,
  Order,
  Page,
  Payment,
  Product,
  ProductQuery,
  ProductRequest,
  SimulatedOutcome,
  Stock,
  UserResponse,
  Profile,
} from './models';

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.catalogUrl;

  products(query: ProductQuery = {}): Observable<Page<Product>> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<Page<Product>>(`${this.base}/api/products/search`, { params });
  }

  product(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.base}/api/products/${encodeURIComponent(id)}`);
  }

  createProduct(request: ProductRequest): Observable<Product> {
    return this.http.post<Product>(`${this.base}/api/products`, request);
  }

  updateProduct(id: string, request: ProductRequest): Observable<Product> {
    return this.http.put<Product>(`${this.base}/api/products/${encodeURIComponent(id)}`, request);
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.base}/api/categories`);
  }

  createCategory(request: CategoryRequest): Observable<Category> {
    return this.http.post<Category>(`${this.base}/api/categories`, request);
  }
}

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.inventoryUrl;

  stock(productId: string): Observable<Stock> {
    return this.http.get<Stock>(`${this.base}/api/inventory/${encodeURIComponent(productId)}`);
  }

  adjust(productId: string, request: AdjustmentRequest): Observable<Stock> {
    return this.http.post<Stock>(
      `${this.base}/api/inventory/${encodeURIComponent(productId)}/adjustments`,
      request,
    );
  }
}

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.cartUrl;

  current(): Observable<Cart> {
    return this.http.get<Cart>(`${this.base}/api/carts/current`);
  }

  addItem(productId: string, quantity: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/api/carts/items`, { productId, quantity });
  }

  updateItem(itemId: string, quantity: number): Observable<Cart> {
    return this.http.put<Cart>(`${this.base}/api/carts/items/${encodeURIComponent(itemId)}`, {
      quantity,
    });
  }

  removeItem(itemId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/api/carts/items/${encodeURIComponent(itemId)}`);
  }

  applyDiscount(code: string): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/api/carts/discount`, { code });
  }

  discounts(): Observable<Discount[]> {
    return this.http.get<Discount[]>(`${this.base}/api/discounts`);
  }

  createDiscount(request: DiscountRequest): Observable<Discount> {
    return this.http.post<Discount>(`${this.base}/api/discounts`, request);
  }
}

@Injectable({ providedIn: 'root' })
export class OrderApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.orderUrl;

  create(idempotencyKey: string, simulatedPaymentOutcome?: SimulatedOutcome): Observable<Order> {
    const body = simulatedPaymentOutcome ? { simulatedPaymentOutcome } : {};
    return this.http.post<Order>(`${this.base}/api/orders`, body, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  get(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/api/orders/${encodeURIComponent(id)}`);
  }

  byCustomer(customerId: string, page = 0, size = 10): Observable<Page<Order>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<Order>>(
      `${this.base}/api/orders/customer/${encodeURIComponent(customerId)}`,
      { params },
    );
  }

  cancel(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/api/orders/${encodeURIComponent(id)}/cancel`, {});
  }

  recover(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/api/orders/${encodeURIComponent(id)}/recover`, {});
  }
}

@Injectable({ providedIn: 'root' })
export class PaymentApi {
  private readonly http = inject(HttpClient);

  byOrder(orderId: string): Observable<Payment[]> {
    const params = new HttpParams().set('orderId', orderId);
    return this.http.get<Payment[]>(`${environment.paymentUrl}/api/payments`, { params });
  }
}

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.identityUrl;

  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(`${this.base}/api/users/me`);
  }

  updateMe(profile: Profile): Observable<UserResponse> {
    return this.http.put<UserResponse>(`${this.base}/api/users/me`, profile);
  }
}

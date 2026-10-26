import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { CheckoutComponent } from './checkout.component';

const cart = {
  id: 'c1',
  customerId: 'u1',
  items: [{ id: 'i1', productId: 'p1', sku: 'ACC-001', productName: 'Mouse', quantity: 2, unitPrice: 1499, lineTotal: 2998 }],
  subtotal: 2998,
  discountCode: null,
  discountAmount: 0,
  total: 2998,
  currency: 'INR',
};

describe('CheckoutComponent', () => {
  let fixture: ComponentFixture<CheckoutComponent>;
  let http: HttpTestingController;
  const ordersUrl = `${environment.orderUrl}/api/orders`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CheckoutComponent);
    fixture.detectChanges();
    http.expectOne(`${environment.cartUrl}/api/carts/current`).flush(cart);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const place = () => (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button')!.click();

  it('shows the server-computed total', () => {
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2,998.00');
  });

  it('reuses the idempotency key after a 503 so no duplicate order is created', () => {
    place();
    const first = http.expectOne(ordersUrl);
    const key = first.request.headers.get('Idempotency-Key');
    expect(key).toBeTruthy();
    first.flush({ code: 'DEPENDENCY_UNAVAILABLE', message: 'Try again later' }, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();

    place();
    const second = http.expectOne(ordersUrl);
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush({}, { status: 503, statusText: 'Unavailable' });
  });

  it('uses a new key and shows the message after a 409 insufficient stock', () => {
    place();
    const first = http.expectOne(ordersUrl);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush({ code: 'INSUFFICIENT_STOCK', message: 'Not enough stock' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Not enough stock');
    place();
    const second = http.expectOne(ordersUrl);
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
    second.flush({}, { status: 409, statusText: 'Conflict' });
  });

  it('navigates to the order after a successful checkout', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    place();
    http.expectOne(ordersUrl).flush({ id: 'o-1', status: 'CONFIRMED' }, { status: 201, statusText: 'Created' });
    http.expectOne(`${environment.cartUrl}/api/carts/current`).flush(cart);

    expect(navigate).toHaveBeenCalledWith(['/orders', 'o-1']);
  });
});

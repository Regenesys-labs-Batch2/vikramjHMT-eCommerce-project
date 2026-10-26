import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { CartComponent } from './cart.component';

const cart = (quantity: number) => ({
  id: 'c1',
  customerId: 'u1',
  items: [{ id: 'i1', productId: 'p1', sku: 'ACC-001', productName: 'Mouse', quantity, unitPrice: 1499, lineTotal: 1499 * quantity }],
  subtotal: 1499 * quantity,
  discountCode: null,
  discountAmount: 0,
  total: 1499 * quantity,
  currency: 'INR',
});

describe('CartComponent', () => {
  let fixture: ComponentFixture<CartComponent>;
  let http: HttpTestingController;
  const cartUrl = `${environment.cartUrl}/api/carts`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CartComponent);
    fixture.detectChanges();
    http.expectOne(`${cartUrl}/current`).flush(cart(1));
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;

  function setQuantity(value: string): void {
    const input = el().querySelector<HTMLInputElement>('tbody input[type=number]')!;
    input.value = value;
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  it('rejects a quantity above 100 without calling the server', () => {
    setQuantity('101');
    http.expectOne(`${cartUrl}/current`).flush(cart(1));
    expect(el().textContent).toContain('between 1 and 100');
  });

  it('rejects a zero quantity without calling the server', () => {
    setQuantity('0');
    http.expectOne(`${cartUrl}/current`).flush(cart(1));
    expect(el().textContent).toContain('between 1 and 100');
  });

  it('updates the quantity and shows the re-priced total', () => {
    setQuantity('3');
    const req = http.expectOne(`${cartUrl}/items/i1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ quantity: 3 });
    req.flush(cart(3));
    fixture.detectChanges();

    expect(el().textContent).toContain('4,497.00');
  });

  it('shows the server message when a discount is not applicable', () => {
    const input = el().querySelector<HTMLInputElement>('#code')!;
    input.value = 'FLAT200';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

    http
      .expectOne(`${cartUrl}/discount`)
      .flush({ code: 'DISCOUNT_NOT_APPLICABLE', message: 'Minimum order total not reached' }, { status: 422, statusText: 'Unprocessable' });
    fixture.detectChanges();

    expect(el().textContent).toContain('Minimum order total not reached');
  });
});

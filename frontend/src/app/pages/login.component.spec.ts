import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let fixture: ComponentFixture<LoginComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  const el = () => fixture.nativeElement as HTMLElement;

  function fill(email: string, password: string): void {
    const email$ = el().querySelector<HTMLInputElement>('#email')!;
    const password$ = el().querySelector<HTMLInputElement>('#password')!;
    email$.value = email;
    email$.dispatchEvent(new Event('input'));
    password$.value = password;
    password$.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('disables submit until the form is valid', () => {
    expect(el().querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeTrue();
    fill('user@example.com', 'secret123');
    expect(el().querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBeFalse();
  });

  it('shows the generic error for invalid credentials', () => {
    fill('user@example.com', 'wrong-password');
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne(`${environment.identityUrl}/api/auth/login`)
      .flush({ code: 'INVALID_CREDENTIALS', message: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(el().querySelector('.alert.error')?.textContent).toContain('Invalid credentials');
  });

  it('navigates to products after a successful login', () => {
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    fill('user@example.com', 'secret123');
    el().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne(`${environment.identityUrl}/api/auth/login`)
      .flush({ accessToken: 't', tokenType: 'Bearer', expiresInSeconds: 3600, userId: 'u', roles: ['CUSTOMER'] });

    expect(navigate).toHaveBeenCalledWith('/products');
  });
});

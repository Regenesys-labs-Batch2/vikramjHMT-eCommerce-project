import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { environment } from '../../environments/environment';
import { apiInterceptor } from './api.interceptor';
import { AuthService } from './auth.service';

describe('apiInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([apiInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => controller.verify());

  function login(): void {
    auth.login('a@b.com', 'secret123').subscribe();
    controller
      .expectOne(`${environment.identityUrl}/api/auth/login`)
      .flush({ accessToken: 'jwt-token', tokenType: 'Bearer', expiresInSeconds: 3600, userId: 'u', roles: ['CUSTOMER'] });
  }

  it('adds the bearer token and a correlation id to service calls', () => {
    login();
    http.get(`${environment.catalogUrl}/api/products`).subscribe();
    const req = controller.expectOne(`${environment.catalogUrl}/api/products`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    expect(req.request.headers.get('X-Correlation-Id')).toBeTruthy();
    req.flush({});
  });

  it('never sends the token to other hosts', () => {
    login();
    http.get('https://example.org/data').subscribe();
    const req = controller.expectOne('https://example.org/data');
    expect(req.request.headers.has('Authorization')).toBeFalse();
    req.flush({});
  });

  it('logs out and redirects to login on 401 for an authenticated call', () => {
    login();
    const navigate = spyOn(router, 'navigate').and.resolveTo(true);
    http.get(`${environment.orderUrl}/api/orders/x`).subscribe({ error: () => undefined });
    controller
      .expectOne(`${environment.orderUrl}/api/orders/x`)
      .flush({ code: 'UNAUTHORIZED' }, { status: 401, statusText: 'Unauthorized' });

    expect(auth.isLoggedIn()).toBeFalse();
    expect(navigate).toHaveBeenCalled();
  });

  it('keeps the session on 403', () => {
    login();
    http.get(`${environment.orderUrl}/api/orders/x`).subscribe({ error: () => undefined });
    controller
      .expectOne(`${environment.orderUrl}/api/orders/x`)
      .flush({ code: 'ACCESS_DENIED' }, { status: 403, statusText: 'Forbidden' });

    expect(auth.isLoggedIn()).toBeTrue();
  });
});

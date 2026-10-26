import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';
import { LoginResponse } from './models';

const loginResponse = (expiresInSeconds: number): LoginResponse => ({
  accessToken: 'jwt-token',
  tokenType: 'Bearer',
  expiresInSeconds,
  userId: 'u-1',
  roles: ['CUSTOMER'],
});

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('stores the session after a successful login', () => {
    service.login('a@b.com', 'secret123').subscribe();
    const req = http.expectOne(`${environment.identityUrl}/api/auth/login`);
    expect(req.request.method).toBe('POST');
    req.flush(loginResponse(3600));

    expect(service.isLoggedIn()).toBeTrue();
    expect(service.isCustomer()).toBeTrue();
    expect(service.isAdmin()).toBeFalse();
    expect(service.token()).toBe('jwt-token');
  });

  it('does not store a session when credentials are rejected', () => {
    let status = 0;
    service.login('a@b.com', 'wrong').subscribe({ error: (e) => (status = e.status) });
    http
      .expectOne(`${environment.identityUrl}/api/auth/login`)
      .flush({ code: 'INVALID_CREDENTIALS' }, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    expect(service.isLoggedIn()).toBeFalse();
    expect(service.token()).toBeNull();
  });

  it('drops an expired token', () => {
    service.login('a@b.com', 'secret123').subscribe();
    http.expectOne(`${environment.identityUrl}/api/auth/login`).flush(loginResponse(-1));

    expect(service.token()).toBeNull();
    expect(service.isLoggedIn()).toBeFalse();
  });

  it('clears the session on logout', () => {
    service.login('a@b.com', 'secret123').subscribe();
    http.expectOne(`${environment.identityUrl}/api/auth/login`).flush(loginResponse(3600));
    service.logout();

    expect(service.token()).toBeNull();
    expect(sessionStorage.getItem('ecom.session')).toBeNull();
  });
});

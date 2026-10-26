import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, roleGuard } from './guards';

describe('guards', () => {
  const state = { url: '/cart' } as RouterStateSnapshot;
  const route = {} as ActivatedRouteSnapshot;

  function setup(roles: string[] | null): void {
    sessionStorage.clear();
    if (roles) {
      sessionStorage.setItem(
        'ecom.session',
        JSON.stringify({ accessToken: 't', userId: 'u', roles, expiresAt: Date.now() + 60_000 }),
      );
    }
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  }

  const run = (guard: typeof authGuard) => TestBed.runInInjectionContext(() => guard(route, state));

  it('authGuard redirects anonymous users to login with the return url', () => {
    setup(null);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Fcart');
  });

  it('authGuard allows authenticated users', () => {
    setup(['CUSTOMER']);
    expect(run(authGuard)).toBeTrue();
  });

  it('roleGuard blocks a customer from admin routes', () => {
    setup(['CUSTOMER']);
    const result = run(roleGuard('ADMIN')) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/');
  });

  it('roleGuard allows an admin on admin routes', () => {
    setup(['ADMIN']);
    expect(run(roleGuard('ADMIN'))).toBeTrue();
  });

  it('roleGuard sends anonymous users to login', () => {
    setup(null);
    const result = run(roleGuard('CUSTOMER')) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toContain('/login');
  });

  it('treats an expired stored session as logged out', () => {
    setup(null);
    sessionStorage.setItem(
      'ecom.session',
      JSON.stringify({ accessToken: 't', userId: 'u', roles: ['CUSTOMER'], expiresAt: Date.now() - 1 }),
    );
    expect(TestBed.inject(AuthService).isLoggedIn()).toBeFalse();
  });
});

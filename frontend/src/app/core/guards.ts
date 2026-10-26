import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from './models';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token() !== null
    ? true
    : router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const roleGuard =
  (role: Role): CanActivateFn =>
  (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (auth.token() === null) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    return auth.hasRole(role) ? true : router.createUrlTree(['/']);
  };

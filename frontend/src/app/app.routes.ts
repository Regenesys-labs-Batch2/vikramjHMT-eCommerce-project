import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'products' },
  {
    path: 'products',
    loadComponent: () =>
      import('./pages/product-list.component').then((m) => m.ProductListComponent),
  },
  {
    path: 'products/:id',
    loadComponent: () =>
      import('./pages/product-detail.component').then((m) => m.ProductDetailComponent),
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/profile.component').then((m) => m.ProfileComponent),
  },
  {
    path: 'cart',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () => import('./pages/cart.component').then((m) => m.CartComponent),
  },
  {
    path: 'checkout',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () => import('./pages/checkout.component').then((m) => m.CheckoutComponent),
  },
  {
    path: 'orders',
    canActivate: [roleGuard('CUSTOMER')],
    loadComponent: () => import('./pages/orders.component').then((m) => m.OrdersComponent),
  },
  {
    path: 'orders/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./pages/order-detail.component').then((m) => m.OrderDetailComponent),
  },
  {
    path: 'admin/catalog',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('./pages/admin/admin-catalog.component').then((m) => m.AdminCatalogComponent),
  },
  {
    path: 'admin/discounts',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('./pages/admin/admin-discounts.component').then((m) => m.AdminDiscountsComponent),
  },
  {
    path: 'admin/orders',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () =>
      import('./pages/admin/admin-orders.component').then((m) => m.AdminOrdersComponent),
  },
  { path: '**', redirectTo: 'products' },
];

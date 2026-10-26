# Frontend (Angular)

Angular 18 standalone-component storefront for the six services. It calls each service directly; the base URLs are in
`src/environments/environment.ts`.

| Area | Routes |
| --- | --- |
| Public | `/products`, `/products/:id`, `/login`, `/register` |
| Customer | `/cart`, `/checkout`, `/orders`, `/orders/:id`, `/profile` |
| Admin | `/admin/catalog` (categories, products, stock), `/admin/discounts`, `/admin/orders` |

Notes:

- CORS: each service allows the origins in `ecom.cors.allowed-origins` (environment variable
  `ECOM_CORS_ALLOWED_ORIGINS`, comma separated, default `http://localhost:4200`).
- The JWT is kept in `sessionStorage` and sent only to the six service URLs. A 401 on an authenticated call logs the
  user out.
- Prices, discounts and totals are always the server's values. Checkout sends a UUID `Idempotency-Key` that is reused
  only when the request failed with a network error or 503.
- Build and tests run on GitHub Actions (`.github/workflows/ci-frontend.yml`).

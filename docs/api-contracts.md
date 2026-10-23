# API contracts (summary)

All endpoints return JSON. Everything except registration, login, health and product/category browsing requires
`Authorization: Bearer <JWT>`. Errors use the standard body described in `engineering-standards.md`.

## Identity (8081)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `POST /api/users/register` | none | 201 `{id, email, roles}`; 409 `EMAIL_ALREADY_EXISTS`; 400 validation |
| `POST /api/auth/login` | none | 200 `{accessToken, tokenType, expiresInSeconds, userId, roles}`; 401 `INVALID_CREDENTIALS` |
| `GET /api/users/me` | CUSTOMER/ADMIN | 200 `{id, email, roles, profile}` |
| `PUT /api/users/me` | CUSTOMER/ADMIN | 200 updated profile; 400 |
| `GET /api/users/{id}` | ADMIN | 200; 404 `USER_NOT_FOUND` |

## Catalog (8082)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `POST /api/categories` | ADMIN | 201; 409 `CATEGORY_ALREADY_EXISTS` |
| `GET /api/categories` | none | 200 list |
| `POST /api/products` | ADMIN | 201; 409 `SKU_ALREADY_EXISTS`; 400 `INVALID_PRICE` |
| `GET /api/products` | none | 200 `{content, page, size, totalElements, totalPages}` (`page`, `size` max 100, `sort`) |
| `GET /api/products/{id}` | none | 200; 404 `PRODUCT_NOT_FOUND` |
| `PUT /api/products/{id}` | ADMIN | 200 (SKU immutable); 404 |
| `GET /api/products/search` | none | `q`, `categoryId`, `minPrice`, `maxPrice`, `active` plus paging |
| `GET /api/products/by-sku/{sku}` | none | 200 |

## Inventory (8083)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `GET /api/inventory/{productId}` | authenticated | 200 `{productId, availableQuantity, reservedQuantity, version}`; 404 `STOCK_NOT_FOUND` |
| `POST /api/inventory/{productId}/adjustments` | ADMIN/SERVICE | 200; 409 `NEGATIVE_STOCK`; same `reference` applied once |
| `POST /api/inventory/reservations` | SERVICE/ADMIN | 201 `{reservationId, status, expiresAt, items}`; 200 for a repeated orderRef; 409 `INSUFFICIENT_STOCK` |
| `POST /api/inventory/reservations/{id}/confirm` | SERVICE/ADMIN | 200 idempotent; 409 `INVALID_RESERVATION_STATE` |
| `POST /api/inventory/reservations/{id}/release` | SERVICE/ADMIN | 200 idempotent; 409 if CONFIRMED |
| `GET /api/inventory/reservations/{id}` | authenticated | 200; 404 |

## Cart and pricing (8084)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `GET /api/carts/current` | CUSTOMER | 200 cart with totals |
| `POST /api/carts/items` | CUSTOMER | 201; 400 `INVALID_QUANTITY`; 404 `PRODUCT_NOT_FOUND` |
| `PUT /api/carts/items/{itemId}` | CUSTOMER | 200; 400 |
| `DELETE /api/carts/items/{itemId}` | CUSTOMER | 204 |
| `POST /api/carts/discount` | CUSTOMER | 200; 404 `DISCOUNT_NOT_FOUND`; 422 `DISCOUNT_NOT_APPLICABLE` |
| `POST /api/carts/checkout-summary` | CUSTOMER/SERVICE | 200 re-priced summary; 422 `EMPTY_CART` |
| `POST /api/carts/{cartId}/checked-out` | SERVICE | 200 (idempotent) |
| `POST /api/discounts`, `GET /api/discounts` | ADMIN | manage discount rules |

Seeded rules: `WELCOME10` (PERCENT 10, minimum 500.00) and `FLAT200` (FIXED 200.00, minimum 2000.00).

## Order (8085)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `POST /api/orders` | CUSTOMER | header `Idempotency-Key` (UUID), optional `{simulatedPaymentOutcome}`; 201; 200 on repeat; 422 `IDEMPOTENCY_CONFLICT` / `EMPTY_CART`; 409 `INSUFFICIENT_STOCK`; 503 `DEPENDENCY_UNAVAILABLE` |
| `GET /api/orders/{id}` | owner/ADMIN | 200 with `statusHistory`; 403; 404 |
| `GET /api/orders/customer/{customerId}` | owner/ADMIN | paginated, newest first |
| `POST /api/orders/{id}/cancel` | owner/ADMIN | 200; 409 `INVALID_ORDER_STATE` |
| `POST /api/orders/{id}/recover` | ADMIN/SERVICE | 200 resumes the pending step |

## Payment (8086)

| Endpoint | Auth | Result |
| --- | --- | --- |
| `POST /api/payments` | SERVICE/ADMIN | header `Idempotency-Key`, `{orderId, customerId, amount, currency, simulatedOutcome?}`; 201; 200 replay; 422 `IDEMPOTENCY_CONFLICT` |
| `GET /api/payments/{id}` | owner/SERVICE/ADMIN | 200 with attempts |
| `GET /api/payments?orderId=` | owner/SERVICE/ADMIN | 200 list |
| `POST /api/payments/{id}/refund` | SERVICE/ADMIN | 201 `{refundId, status}`; 200 existing refund; 409 `INVALID_PAYMENT_STATE` |

Simulation: `simulatedOutcome` wins (`SUCCESS`, `DECLINED` -> `CARD_DECLINED`, `TIMEOUT` -> sleeps
`PAYMENT_SIMULATED_TIMEOUT_MS` then records SUCCEEDED); otherwise an amount ending in `.99` is declined, `.98` behaves
as a timeout, anything else succeeds.

## Seed data

Admin `admin@ecom.test` (password from `SEED_ADMIN_PASSWORD`, default `Admin#12345`); categories Laptops and
Accessories; products `LAP-001` 55000.00 (stock 5), `LAP-002` 72999.99 (stock 1), `ACC-001` 1499.00 (stock 50),
`ACC-002` 899.00 (stock 100) with ids `11111111-0000-0000-0000-000000000001` to `...0004`.

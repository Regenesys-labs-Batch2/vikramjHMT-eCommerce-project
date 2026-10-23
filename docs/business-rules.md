# Business rules

Each rule has an ID, a statement, the error code returned when violated, and the HTTP status.
Tests that prove a rule or acceptance criterion start their `@DisplayName` with its IDs.

## Identity

- R-IDN-01 Email is unique (case-insensitive). `EMAIL_ALREADY_EXISTS`, 409.
- R-IDN-02 Password minimum 8 characters, stored only as a BCrypt hash. `VALIDATION_ERROR`, 400.
- R-IDN-03 A user can read or update only their own profile unless ADMIN. `FORBIDDEN`, 403.
- R-IDN-04 New registrations receive role CUSTOMER only.

## Catalog

- R-CAT-01 SKU is unique, upper-case, pattern `[A-Z]{3}-[0-9]{3}`, and immutable. `SKU_ALREADY_EXISTS`, 409.
- R-CAT-02 Price amount > 0 with at most 2 decimals; currency INR. `INVALID_PRICE`, 400.
- R-CAT-03 Only ADMIN creates or updates products and categories. `FORBIDDEN`, 403.
- R-CAT-04 Search is case-insensitive on name; pagination max size 100; sort only on whitelisted fields.

## Inventory

- R-INV-01 availableQuantity and reservedQuantity are never negative. `INSUFFICIENT_STOCK`, 409.
- R-INV-02 Reserve, confirm and release are atomic per product (conditional UPDATE) and protected by `@Version`.
- R-INV-03 A reservation request with an existing orderRef returns the existing reservation.
- R-INV-04 confirm allowed from PENDING or CONFIRMED (idempotent); release allowed from PENDING, RELEASED or
  EXPIRED (idempotent: RELEASED and EXPIRED return 200 unchanged); confirm from RELEASED/EXPIRED or release from
  CONFIRMED: `INVALID_RESERVATION_STATE`, 409.
- R-INV-05 PENDING reservations expire after `INVENTORY_RESERVATION_TTL_MINUTES` (default 15).
- R-INV-06 Adjustments by ADMIN or SERVICE only; a repeated adjustment with the same reference returns the
  original result and does not change stock again. `NEGATIVE_STOCK`, 409.
- R-INV-07 Reservation create/confirm/release accept SERVICE and ADMIN only. `FORBIDDEN`, 403.

## Cart and pricing

- R-CART-01 Quantity per item 1..100. `INVALID_QUANTITY`, 400.
- R-CART-02 Only active products can be added. `PRODUCT_NOT_FOUND`, 404.
- R-CART-03 unitPrice is re-read from Catalog at checkout; client totals are ignored. An empty cart returns `EMPTY_CART`, 422.
- R-CART-04 PERCENT discount = subtotal x value / 100 (HALF_UP, 2 dp). FIXED = min(value, subtotal).
- R-CART-05 A discount applies only if active, within validity, and subtotal >= minimumOrderTotal.
  `DISCOUNT_NOT_APPLICABLE`, 422.
- R-CART-06 total = subtotal - discountAmount, never below 0.00.

## Orders

- R-ORD-01 Idempotency-Key header is required; same key returns the existing order (200); same key + different
  request body (hash): `IDEMPOTENCY_CONFLICT`, 422.
- R-ORD-02 Legal transitions are only those in docs/architecture.md state table. `INVALID_ORDER_STATE`, 409.
- R-ORD-03 An order is CONFIRMED only after payment SUCCEEDED and reservation CONFIRMED.
- R-ORD-04 Payment DECLINED -> release reservation -> CANCELLED; release failure -> COMPENSATION_PENDING (recoverable).
- R-ORD-05 A payment timeout is reconciled (same Idempotency-Key / query by orderId) before any decision.
- R-ORD-06 Cancellation allowed from CONFIRMED (refund + restock), PAYMENT_FAILED, COMPENSATION_PENDING.
- R-ORD-07 Inventory unreachable during reservation: order CANCELLED (INVENTORY_UNAVAILABLE), 503.
- R-ORD-08 Payment SUCCEEDED but reservation EXPIRED/RELEASED: refund, then CANCELLED (RESERVATION_LOST); never
  CONFIRMED without stock.
- R-ORD-09 Marking the cart checked-out is best effort and never fails a CONFIRMED order.

## Payments

- R-PAY-01 Idempotency-Key required; same key + same requestHash returns original (200); same key + different
  hash: `IDEMPOTENCY_CONFLICT`, 422.
- R-PAY-02 Outcome: simulatedOutcome wins; else amount ending .99 DECLINED, .98 TIMEOUT behaviour, else SUCCEEDED.
- R-PAY-03 Refund only from SUCCEEDED; a repeated refund returns the existing refund (look it up before the state
  check). `INVALID_PAYMENT_STATE`, 409.
- R-PAY-04 No real payment provider is ever called.
- R-PAY-05 Create and refund accept SERVICE and ADMIN only; a CUSTOMER may read only payments whose customerId
  equals the token subject. `FORBIDDEN`, 403.

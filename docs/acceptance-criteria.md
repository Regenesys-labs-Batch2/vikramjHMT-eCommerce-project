# Acceptance criteria

## Identity

- AC-IDN-01 Duplicate registration is rejected (409).
- AC-IDN-02 Incorrect credentials do not authenticate users (401, no detail about which part was wrong).
- AC-IDN-03 Expired or invalid tokens are rejected (401) by every service.
- AC-IDN-04 Customers cannot modify other customers' profiles (`/me` only operates on the token subject).
- AC-IDN-05 Customer accounts cannot execute administrator-only operations (403).

## Catalog

- AC-CAT-01 A product cannot have an invalid price.
- AC-CAT-02 Duplicate SKUs are rejected.
- AC-CAT-03 Invalid products return meaningful errors (standard error body with details).
- AC-CAT-04 Search and pagination produce consistent results (stable sort, same total across pages).
- AC-CAT-05 Unauthorised users cannot modify catalog information.

## Inventory

- AC-INV-01 Reservations cannot exceed available stock.
- AC-INV-02 Concurrent requests cannot oversell products.
- AC-INV-03 Reservation confirmation is idempotent.
- AC-INV-04 Releasing a reservation twice does not increase stock twice.
- AC-INV-05 Invalid state transitions are rejected.

## Cart and pricing

- AC-CART-01 Invalid quantities are rejected.
- AC-CART-02 Discounts follow the specified business rules.
- AC-CART-03 Monetary calculations remain consistent (BigDecimal, scale 2, HALF_UP everywhere).
- AC-CART-04 The checkout total is calculated using trusted pricing information (Catalog price at checkout time).
- AC-CART-05 Cart changes do not corrupt existing orders (orders keep their own price snapshots).

## Orders

- AC-ORD-01 Orders are not confirmed without successful payment.
- AC-ORD-02 Inventory reservation occurs before payment confirmation.
- AC-ORD-03 Payment failure triggers appropriate compensation.
- AC-ORD-04 Order status accurately represents the workflow.
- AC-ORD-05 Invalid state transitions are rejected.
- AC-ORD-06 Duplicate requests do not create multiple logical orders.

## Payments

- AC-PAY-01 The same idempotency key and request cannot generate duplicate payment transactions.
- AC-PAY-02 Reusing a key with conflicting request details is rejected.
- AC-PAY-03 Payment failures are recorded (PaymentAttempt row and failureReason).
- AC-PAY-04 Refund requests follow valid transaction states.
- AC-PAY-05 No actual financial transactions are performed.

## End-to-end scenarios

Automated by `scripts/smoke-test.sh`, `scripts/concurrency-test.sh` and the `integration` workflow: registration,
authentication, catalog management, product search, successful checkout, insufficient stock, concurrent checkout,
payment failure, duplicate request, payment timeout reconciliation, service timeout, unauthorised access and order
cancellation.

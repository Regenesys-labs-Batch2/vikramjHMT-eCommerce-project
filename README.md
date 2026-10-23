# Enterprise E-Commerce platform

Six Spring Boot 3.5 / Java 21 microservices built from the Happiest Minds E-Commerce Java project guide (v1.40).
Every service is an independent Maven project with its own H2 database; there is no parent POM.

| Folder | Port | Purpose |
| --- | --- | --- |
| `identity-service` | 8081 | Registration, login, JWT, profiles, roles |
| `catalog-service` | 8082 | Products, categories, search |
| `inventory-service` | 8083 | Stock, reservations, adjustments |
| `cart-service` | 8084 | Cart, pricing, discounts, checkout summary |
| `order-service` | 8085 | Checkout saga, order states, cancellation, recovery |
| `payment-service` | 8086 | Simulated payments and refunds |

Documentation is in `docs/` (architecture, API contracts, business rules, acceptance criteria, engineering standards).

## Build and test

Compilation and tests run on GitHub Actions: one reusable workflow (`.github/workflows/service-ci.yml`) and a thin
caller per service (`ci-<service>.yml`, triggered only by changes in that folder). Each run executes
`mvn -B -ntp -U verify`: unit tests (`*Test`) in the test phase, integration tests (`*IT`) through Failsafe, then
packages the executable JAR.

End-to-end validation is the manually started `integration` workflow. It packages and starts all six services on the
runner (Identity, Catalog, Inventory, Cart, Payment, Order), then runs `scripts/smoke-test.sh`,
`scripts/concurrency-test.sh` and an inventory-down scenario. It needs the repository secret `JWT_SECRET` (at least 32
characters) and optionally `SEED_ADMIN_PASSWORD`. Service logs are uploaded as the `service-logs` artifact.

Pull-request titles must read `<type>: <summary>` (feat, fix, test, docs, refactor, chore, ci); the `pr-title` job in
`service-ci.yml` enforces it.

Releases: push an annotated tag `<service>-vMAJOR.MINOR.PATCH` on `main` (for example `identity-service-v1.0.0`). The
`release` workflow builds with `-Drevision=<version>`, checks `build.version` in the JAR, and publishes a GitHub Release
with the JAR, `SHA256SUMS` and notes. Local and CI builds use `0.1.0-SNAPSHOT`; `/actuator/info` reports the version.

## Runtime configuration

| Variable | Default | Used by |
| --- | --- | --- |
| `JWT_SECRET` | local dev value | all (minimum 32 characters; set a real secret outside local use) |
| `JWT_EXPIRY_MINUTES` | 60 | identity |
| `SEED_ADMIN_PASSWORD` | `Admin#12345` | identity seeder, scripts |
| `SERVER_PORT` | service port | all |
| `IDENTITY_URL` ... `PAYMENT_URL` | `http://localhost:808x` | clients |
| `CLIENT_CONNECT_TIMEOUT_MS` / `CLIENT_READ_TIMEOUT_MS` | 1000 / 2000 | clients |
| `INVENTORY_RESERVATION_TTL_MINUTES` | 15 | inventory |
| `PAYMENT_SIMULATED_TIMEOUT_MS` | 5000 | payment |
| `ORDER_RECOVERY_INTERVAL_MS` | 60000 | order |

Start order for a manual run: identity, catalog, inventory, cart, payment, order; each exposes
`/actuator/health`. Runtime data is written to `./data/<service>db` (git-ignored).

## Key design points

- Inventory prevents overselling with conditional `UPDATE ... WHERE availableQuantity >= :qty` statements and
  `@Version` on reservations.
- Order runs an orchestrated saga with persisted states, compensation (release, refund, restock) and a recovery
  endpoint and job; no database transaction spans a remote call.
- Idempotency keys protect order creation and payments: a retried request returns the original transaction.
- Customer identity always comes from the JWT subject; reservation and payment write endpoints accept only SERVICE
  and ADMIN tokens.

Not included: the optional BULK5 enhancement, Docker/Kubernetes packaging, deployment pipelines and the optional
notification capability (guide Days 4-5).

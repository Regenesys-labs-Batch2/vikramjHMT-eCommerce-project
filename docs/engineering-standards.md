# Engineering standards

## Conventions

| Convention | Standard |
| --- | --- |
| Group / package | `com.happiestminds.ecom.<service>` with sub-packages `api`, `service`, `domain`, `repository`, `security`, `client`, `config` |
| Java version | 21 (records for DTOs, `var` allowed, no Lombok) |
| Spring Boot | 3.5.16 (`spring-boot-starter-parent`), Jakarta EE namespaces |
| Build | Maven, `mvn -B -ntp verify` on GitHub Actions; no `mvnw` wrapper |
| Persistence | Spring Data JPA, H2; tests use `jdbc:h2:mem`, runtime uses `jdbc:h2:file:./data/<service>db;LOCK_TIMEOUT=10000` |
| Money | `java.math.BigDecimal`, scale 2, `RoundingMode.HALF_UP`, currency INR |
| IDs | UUID strings for all entity primary keys |
| Timestamps | `java.time.Instant` in UTC, fields `createdAt`, `updatedAt` |
| Error body | `{ "timestamp", "status", "error", "code", "message", "path", "correlationId", "details": [] }` |
| Correlation | Header `X-Correlation-Id`: read if present, otherwise generated; put in MDC; propagated on every outbound call |
| Security | HS256 JWT, secret from `JWT_SECRET`, roles CUSTOMER, ADMIN, SERVICE |
| Health | Actuator `/actuator/health` and `/actuator/info` exposed without authentication |
| Tests | JUnit 5, Spring Boot Test, MockMvc, Mockito; integration tests named `*IT` and run in the verify phase |

## Approved dependencies

`spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`,
`spring-boot-starter-security`, `spring-boot-starter-actuator`, `com.h2database:h2` (runtime),
`io.jsonwebtoken:jjwt-api|jjwt-impl|jjwt-jackson` 0.12.6, `spring-boot-starter-test` (test),
`spring-security-test` (test), `maven-failsafe-plugin`.

Not approved: Lombok, MapStruct, Flyway/Liquibase, Resilience4j, Testcontainers, WireMock, OpenFeign, Kafka, Redis clients.

## Approved AI model

GPT-5.6 Luna (or the replacement approved by the trainer; record it here).

## CI

Builds and tests run on GitHub Actions (`.github/workflows/service-ci.yml`, one thin caller workflow per service).

## Commit format

`<type>: <summary>` where type is feat, fix, test, docs, refactor, chore or ci.

## Design decisions worth knowing

- Entities use Hibernate-generated UUIDs (`GenerationType.UUID`) so `save()` persists instead of merging. The two
  exceptions use assigned ids on purpose: Catalog `Product` (fixed seed ids) and Inventory `InventoryStock`.
- Stock changes are single conditional `UPDATE` statements whose row count decides success; `@Version` protects the
  reservation rows and orders.
- The Order service never holds a database transaction across a remote call: every persisted state change is its own
  short transaction in `OrderTransactions`.
- `RestClient.Builder` is a prototype bean so every client configures its own base URL.

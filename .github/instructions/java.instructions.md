---
applyTo: "**/*.java"
---
- Java 21: use records for DTOs, enums for states, switch expressions, text blocks. No Lombok.
- Constructor injection only (no `@Autowired` on fields). One public class per file.
- Validation annotations on request records (`@NotBlank`, `@Email`, `@Positive`, `@DecimalMin`, `@Size`). Controllers
  use `@Valid` and `@Validated`.
- `@Transactional` only on service methods; never wrap a remote HTTP call inside a transaction.
- Entities: `@Id @GeneratedValue(strategy = GenerationType.UUID) String id`; `@Version Long version` where concurrent
  updates are possible; `Instant createdAt/updatedAt` with `@PrePersist/@PreUpdate`. Always use the value returned by
  `save()`.
- Repositories extend `JpaRepository`; custom queries use `@Query` with named parameters; paginated queries return
  `Page<T>`. Avoid H2 reserved words as column names (`value`, `key`, `order`, `user`).
- Do not catch and swallow exceptions. Translate exceptions at the service boundary into domain exceptions with a code.
- Use `Optional` only as a return type. Avoid null returns from service methods.

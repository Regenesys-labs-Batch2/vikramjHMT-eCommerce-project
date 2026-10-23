---
applyTo: "**/src/test/**"
---
- JUnit 5 + AssertJ. Test class name = `<ClassUnderTest>Test` for unit tests, `<Feature>IT` for Spring Boot
  integration tests (run by Failsafe in the verify phase).
- Method names describe behaviour: `shouldRejectDuplicateSku_whenSkuExists()`.
- Tests that prove an acceptance criterion or business rule start their `@DisplayName` with its IDs, for example
  `@DisplayName("AC-INV-01 R-INV-01 rejects reservation above available stock")`.
- Arrange-Act-Assert with blank lines between sections. One behaviour per test.
- Unit tests mock repositories and clients with Mockito; no Spring context.
- Integration tests use `@SpringBootTest` + `@AutoConfigureMockMvc` with the H2 in-memory profile `test`. Outbound
  clients are replaced with `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`).
- When re-stubbing a Mockito mock that already throws, use `doReturn(...).when(mock).method(...)`.
- Every endpoint gets: happy path, validation failure (400), unauthenticated (401), wrong role (403), not found (404),
  and invalid state (409) where applicable.
- Concurrency tests use an `ExecutorService` with a `CountDownLatch` and assert exact counts.
- Tests never call the network and never depend on order. Never disable a failing test.

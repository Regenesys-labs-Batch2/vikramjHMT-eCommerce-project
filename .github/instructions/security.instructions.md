---
applyTo: "**/security/**/*.java,**/*Controller.java,**/config/**/*.java,**/application*.yml"
---
- Stateless Spring Security: `SessionCreationPolicy.STATELESS`, CSRF disabled, no form login.
- Public paths only: `/actuator/health`, `/actuator/info`, `POST /api/users/register`, `POST /api/auth/login`,
  `GET /api/products/**`, `GET /api/categories/**`. Everything else requires a valid bearer JWT.
- Roles: CUSTOMER, ADMIN, SERVICE. Use `@PreAuthorize("hasRole('ADMIN')")` for admin endpoints and
  `hasAnyRole('SERVICE','ADMIN')` for internal workflow endpoints (inventory reservations, payment create/refund).
  Never allow CUSTOMER there.
- Ownership: compare the path or body customerId with the JWT subject unless the caller has ROLE_ADMIN or ROLE_SERVICE.
  Never trust a client-supplied userId.
- JWT: HS256, secret from `JWT_SECRET` (minimum 32 chars), expiry from `JWT_EXPIRY_MINUTES`. Reject expired or malformed
  tokens with 401 through the standard error body. Never log tokens.
- Passwords: `BCryptPasswordEncoder`; never return `passwordHash` in any response.
- Error responses never contain stack traces, SQL, or internal class names.
- Actuator exposure: `management.endpoints.web.exposure.include=health,info` only.

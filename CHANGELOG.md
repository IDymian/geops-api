# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

## [0.3.1] - 2026-11-06

Maintenance release.

### Removed

- Dependabot configuration: dependency updates are now done by hand.

## [0.3.0] - 2026-11-06

Third sprint (Day 3 of the MVP): coupons, redemption, stats and deployment. This release completes the MVP.

### Added

- Coupons for offers: providers issue percentage or fixed-amount coupons with optional stock, a per-user limit and a validity window inside the offer's period (`POST /api/v1/businesses/me/offers/{offerId}/coupons`, `GET /api/v1/businesses/me/coupons`).
- Consumers see the coupons of a live offer (`GET /api/v1/offers/{offerId}/coupons`), claim one (`POST /api/v1/coupons/{couponId}/claim`) and list their own (`GET /api/v1/users/me/coupons`).
- Each claim gets a unique 8-character code without look-alike characters (`0/O`, `1/I/L`), generated with `SecureRandom`; dashes, spaces and lowercase are accepted when redeeming.
- Redemption at the counter (`POST /api/v1/businesses/me/redemptions`): returns the customer's first name and the offer price; another business's code answers like an unknown one.
- Stock and double redemption are protected with pessimistic row locks; an integration test runs 8 concurrent claims against a stock of 3.
- Audit log (`audit_log` table, JSONB metadata) with an entry for every redemption.
- Business stats (`GET /api/v1/businesses/me/stats`): totals, redemption rate, the last 7 days in Lima time and the top 5 offers.
- Flyway migration `V4` for coupons, user coupons and the audit log.
- Render Blueprint (`render.yaml`): Docker web service plus a private Key Value store, deployed from `main` only after CI passes.
- `prod` profile: Redis from `REDIS_URL`, small connection pool, forwarded headers and graceful shutdown; the port also reads Render's `PORT`.
- Step-by-step deployment guide for Render and Neon in `docs/deploy.md`.

## [0.2.0] - 2026-11-05

Second sprint (Day 2 of the MVP): businesses, offers and radius search.

### Added

- Business panel for providers under `/api/v1/businesses/me`: create or update the business profile and its location, list, publish, edit and pause offers.
- Public catalog: `GET /api/v1/categories` (9 seeded categories, cached in Redis), `GET /api/v1/offers/nearby` and `GET /api/v1/offers/{id}`.
- Radius search with PostGIS: business location stored as a generated `geography(Point,4326)` column with a GiST index, queried with `ST_DWithin` and ordered by real distance in meters (100 m to 20 km, at most 100 results).
- Only live offers (active and within their period) reach consumers; a provider addressing another provider's offer gets `404`.
- Flyway migration `V3` for businesses, categories and offers.
- Validation errors on query parameters now return problem details with the `VALIDATION_FAILED` code.

### Changed

- CI uses `actions/upload-artifact` v7.
- Dependabot no longer proposes major upgrades of the Java runtime image (the stack stays on Java 21 LTS).

## [0.1.0] - 2026-11-04

First sprint (Day 1 of the MVP): project foundation and authentication.

### Added

- Spring Boot 4.1 / Java 21 project organized by bounded contexts, with a versioned REST API under `/api/v1`.
- Authentication: register as `CONSUMER` or `PROVIDER`, login, refresh and logout (`/api/v1/auth/*`), and `GET /api/v1/users/me`.
- HS256 JWT access tokens (15 min) validated by Spring Security; role-based access with `@PreAuthorize`.
- Refresh tokens stored only as SHA-256 hashes, rotated on every use, with reuse detection that revokes the whole session.
- Login rate limiting in Redis (5 failed attempts per minute per IP and email, `429` with `Retry-After`).
- Global error handling with RFC 9457 problem details and stable error codes.
- Restrictive CORS, stateless security, BCrypt password hashing, secrets only from environment variables.
- Swagger UI at `/swagger-ui.html`.
- Flyway migrations: PostGIS extension (`V1`) and identity tables (`V2`).
- ER data model for the whole project in `docs/data-model.md`.
- Docker image (non-root), `docker-compose.yml` with PostGIS and Redis.
- CI on GitHub Actions (build, Testcontainers tests, Docker image) and Dependabot.
- Nightly backup scripts to the `wip/nightly` branch.

[0.3.1]: https://github.com/geops-app/geops-api/releases/tag/v0.3.1
[0.3.0]: https://github.com/geops-app/geops-api/releases/tag/v0.3.0
[0.2.0]: https://github.com/geops-app/geops-api/releases/tag/v0.2.0
[0.1.0]: https://github.com/geops-app/geops-api/releases/tag/v0.1.0

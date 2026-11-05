# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

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

[0.2.0]: https://github.com/geops-app/geops-api/releases/tag/v0.2.0
[0.1.0]: https://github.com/geops-app/geops-api/releases/tag/v0.1.0

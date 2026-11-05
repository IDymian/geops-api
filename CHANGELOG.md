# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).

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

[0.1.0]: https://github.com/geops-app/geops-api/releases/tag/v0.1.0

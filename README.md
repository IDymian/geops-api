# GeoPS API

> 🇪🇸 [Español](#español) · 🇬🇧 [English](#english)

[![CI](https://github.com/geops-app/geops-api/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/geops-app/geops-api/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/geops-app/geops-api)](https://github.com/geops-app/geops-api/releases)
![Java](https://img.shields.io/badge/Java-21-red)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Español

**GeoPS** conecta negocios locales con consumidores mediante ofertas y cupones hiperlocales según su ubicación. Este repositorio contiene la API REST que usa la app móvil [geops-mobile](https://github.com/geops-app/geops-mobile).

**Estado:** MVP completo (`v0.3.x`). Cambios por versión en el [CHANGELOG](CHANGELOG.md).

### Funcionalidades

- **Cuentas:** registro como consumidor o negocio, login, refresh y logout con JWT, y límite de intentos de login.
- **Consumidor:** ofertas cercanas por radio (PostGIS) con filtro por categoría, detalle de oferta, obtener cupones y ver los propios con su código.
- **Negocio:** perfil y ubicación del negocio, publicar, editar y pausar ofertas, crear cupones (porcentaje o monto, con stock y límite por usuario), canjear códigos en caja y ver estadísticas de los últimos 7 días.
- **Integridad:** el stock y los canjes usan bloqueo de fila, así que un cupón nunca se entrega de más ni se canjea dos veces. Cada canje queda en un registro de auditoría.

**Próximamente:** favoritos, reseñas, carrito, suscripción a negocios, pagos (Stripe en modo test) y notificaciones.

### Endpoints principales

Todos bajo `/api/v1`. La referencia completa está en Swagger UI.

| Área | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` · `GET /users/me` |
| Catálogo | `GET /categories`, `/offers/nearby`, `/offers/{id}` |
| Cupones (consumidor) | `GET /offers/{id}/coupons` · `POST /coupons/{id}/claim` · `GET /users/me/coupons` |
| Negocio | `GET/PUT /businesses/me` · `GET/POST /businesses/me/offers` · `PUT /businesses/me/offers/{id}` |
| Cupones (negocio) | `POST /businesses/me/offers/{id}/coupons` · `GET /businesses/me/coupons` · `POST /businesses/me/redemptions` · `GET /businesses/me/stats` |

Los errores siguen RFC 9457 (*problem details*) con un campo `code` estable, por ejemplo `COUPON_SOLD_OUT`.

### Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1 (Web, Security, Data JPA, Validation) |
| Autenticación | JWT HS256 (15 min) con refresh tokens rotados y guardados como hash |
| Base de datos | PostgreSQL + PostGIS, migraciones con Flyway |
| Caché / rate limiting | Redis |
| Documentación | springdoc-openapi (Swagger UI) |
| Pruebas | JUnit 5 + Testcontainers (PostGIS y Redis reales) |
| Despliegue | Docker en Render + Neon |

### Arquitectura

Monolito modular organizado por *bounded contexts*: `identity`, `offers` y `coupons`, más `shared` (seguridad, errores, auditoría, configuración). Cada contexto tiene las capas `api`, `application`, `domain` e `infrastructure`.

Modelo de datos (diagrama ER y decisiones): [docs/data-model.md](docs/data-model.md).

### Cómo ejecutar

```bash
cp .env.example .env   # completa los valores
docker compose up -d   # PostgreSQL + PostGIS y Redis
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

Pruebas (necesitan Docker): `./mvnw verify`

### Despliegue

Render (API + Redis) y Neon (PostgreSQL + PostGIS), con despliegue automático desde `main` cuando el CI pasa. Guía paso a paso: [docs/deploy.md](docs/deploy.md).

### Flujo de trabajo con Git

- `main`: versión estable. Solo recibe `develop` en cada release, con un tag semántico (`v0.1.0`, `v0.2.0`…).
- `develop`: integración. Cada funcionalidad llega mediante pull request desde una rama `feature/*`, con CI obligatorio.
- `wip/nightly`: **solo es un respaldo automático nocturno** de trabajo sin terminar. La historia real del proyecto está en las ramas `feature/*` y en sus pull requests. Detalles: [docs/nightly-backup.md](docs/nightly-backup.md).

### Licencia

[MIT](LICENSE)

---

## English

**GeoPS** connects local businesses with consumers through hyperlocal, location-based offers and coupons. This repository holds the REST API used by the [geops-mobile](https://github.com/geops-app/geops-mobile) app.

**Status:** MVP complete (`v0.3.x`). Changes per version in the [CHANGELOG](CHANGELOG.md).

### Features

- **Accounts:** sign up as a consumer or a business, login, refresh and logout with JWT, and login rate limiting.
- **Consumer:** nearby offers by radius (PostGIS) with a category filter, offer details, claim coupons and list your own with their code.
- **Business:** business profile and location, publish, edit and pause offers, issue coupons (percentage or fixed amount, with stock and a per-user limit), redeem codes at the counter and see stats for the last 7 days.
- **Integrity:** stock and redemptions use row locks, so a coupon is never over-issued or redeemed twice. Every redemption is written to an audit log.

**Coming next:** favorites, reviews, cart, business subscriptions, payments (Stripe test mode) and notifications.

### Main endpoints

All under `/api/v1`. The full reference is in Swagger UI.

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` · `GET /users/me` |
| Catalog | `GET /categories`, `/offers/nearby`, `/offers/{id}` |
| Coupons (consumer) | `GET /offers/{id}/coupons` · `POST /coupons/{id}/claim` · `GET /users/me/coupons` |
| Business | `GET/PUT /businesses/me` · `GET/POST /businesses/me/offers` · `PUT /businesses/me/offers/{id}` |
| Coupons (business) | `POST /businesses/me/offers/{id}/coupons` · `GET /businesses/me/coupons` · `POST /businesses/me/redemptions` · `GET /businesses/me/stats` |

Errors follow RFC 9457 problem details with a stable `code` field, such as `COUPON_SOLD_OUT`.

### Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 (Web, Security, Data JPA, Validation) |
| Auth | HS256 JWT (15 min) with rotated refresh tokens stored as hashes |
| Database | PostgreSQL + PostGIS, Flyway migrations |
| Cache / rate limiting | Redis |
| Docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5 + Testcontainers (real PostGIS and Redis) |
| Deployment | Docker on Render + Neon |

### Architecture

Modular monolith split into bounded contexts: `identity`, `offers` and `coupons`, plus `shared` (security, errors, audit, configuration). Each context has `api`, `application`, `domain` and `infrastructure` layers.

Data model (ER diagram and decisions): [docs/data-model.md](docs/data-model.md).

### Getting started

```bash
cp .env.example .env   # fill in the values
docker compose up -d   # PostgreSQL + PostGIS and Redis
./mvnw spring-boot:run
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

Tests (Docker required): `./mvnw verify`

### Deployment

Render (API + Redis) and Neon (PostgreSQL + PostGIS), deployed automatically from `main` when CI passes. Step-by-step guide: [docs/deploy.md](docs/deploy.md).

### Git workflow

- `main`: stable. Receives `develop` only on each release, with a semantic tag (`v0.1.0`, `v0.2.0`…).
- `develop`: integration. Every feature arrives through a pull request from a `feature/*` branch, with required CI.
- `wip/nightly`: **only an automatic nightly backup** of unfinished work. The real project history lives in the `feature/*` branches and their pull requests. Details: [docs/nightly-backup.md](docs/nightly-backup.md).

### License

[MIT](LICENSE)

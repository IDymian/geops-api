# GeoPS API

> 🇪🇸 [Español](#español) · 🇬🇧 [English](#english)

![Status](https://img.shields.io/badge/status-in%20development-orange)
![Java](https://img.shields.io/badge/Java-21-red)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

---

## Español

**GeoPS** conecta negocios locales con consumidores mediante ofertas y cupones hiperlocales según su ubicación. Este repositorio contiene la API REST que usa la app móvil [geops-mobile](https://github.com/IDymian/geops-mobile).

### Funcionalidades

- **Consumidor:** ofertas cercanas por radio, filtros por categoría y distancia, favoritos, reseñas, carrito, canje de cupones, suscripción a negocios y pagos.
- **Proveedor:** gestión de ofertas, cupones y perfil del negocio, con estadísticas de canjes.

### Stack

| Capa | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3 (Web, Security, Data JPA, Validation) |
| Autenticación | JWT con refresh tokens |
| Base de datos | PostgreSQL + PostGIS, migraciones con Flyway |
| Caché / rate limiting | Redis |
| Documentación | springdoc-openapi (Swagger UI) |
| Pagos | Stripe (modo test) y flujo simulado de Yape |
| Pruebas | JUnit 5 + Testcontainers |

### Arquitectura

Monolito modular organizado por *bounded contexts*: `identity`, `offers`, `coupons`, `reviews`, `subscriptions` y `payments`. Cada contexto tiene las capas `api`, `application`, `domain` e `infrastructure`. La API está versionada bajo `/api/v1`.

### Cómo ejecutar

> 🚧 En construcción. Se completará con Docker Compose en el primer sprint.

```bash
cp .env.example .env   # completa los valores
docker compose up -d   # PostgreSQL + PostGIS y Redis
./mvnw spring-boot:run
```

### Flujo de trabajo con Git

- `main`: versión estable. Solo recibe `develop` al cerrar cada sprint, con un tag semántico (`v0.1.0`, `v0.2.0`…).
- `develop`: integración. Cada funcionalidad llega mediante pull request desde una rama `feature/*`.
- `wip/nightly`: **solo es un respaldo automático nocturno** de trabajo sin terminar. La historia real del proyecto está en las ramas `feature/*` y en sus pull requests. Detalles: [docs/nightly-backup.md](docs/nightly-backup.md).

### Licencia

[MIT](LICENSE)

---

## English

**GeoPS** connects local businesses with consumers through hyperlocal, location-based offers and coupons. This repository holds the REST API used by the [geops-mobile](https://github.com/IDymian/geops-mobile) app.

### Features

- **Consumer:** nearby offers by radius, category and distance filters, favorites, reviews, cart, coupon redemption, business subscriptions and payments.
- **Provider:** manage offers, coupons and the business profile, with basic redemption stats.

### Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3 (Web, Security, Data JPA, Validation) |
| Auth | JWT with refresh tokens |
| Database | PostgreSQL + PostGIS, Flyway migrations |
| Cache / rate limiting | Redis |
| Docs | springdoc-openapi (Swagger UI) |
| Payments | Stripe (test mode) and a simulated Yape flow |
| Testing | JUnit 5 + Testcontainers |

### Architecture

Modular monolith split into bounded contexts: `identity`, `offers`, `coupons`, `reviews`, `subscriptions` and `payments`. Each context has `api`, `application`, `domain` and `infrastructure` layers. The API is versioned under `/api/v1`.

### Getting started

> 🚧 Work in progress. Docker Compose setup lands in the first sprint.

```bash
cp .env.example .env   # fill in the values
docker compose up -d   # PostgreSQL + PostGIS and Redis
./mvnw spring-boot:run
```

### Git workflow

- `main`: stable. Receives `develop` only at the end of each sprint, with a semantic tag (`v0.1.0`, `v0.2.0`…).
- `develop`: integration. Every feature arrives through a pull request from a `feature/*` branch.
- `wip/nightly`: **only an automatic nightly backup** of unfinished work. The real project history lives in the `feature/*` branches and their pull requests. Details: [docs/nightly-backup.md](docs/nightly-backup.md).

### License

[MIT](LICENSE)

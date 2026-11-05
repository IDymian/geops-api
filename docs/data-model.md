# Data model / Modelo de datos

> 🇪🇸 [Español](#español) · 🇬🇧 [English](#english)

The full model is designed up front; tables are created with Flyway only when the feature that uses them lands.

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "has"
    USERS ||--o| BUSINESSES : "owns (PROVIDER)"
    USERS ||--o{ USER_COUPONS : "claims"
    USERS ||--o{ FAVORITES : "saves"
    USERS ||--o{ REVIEWS : "writes"
    USERS ||--o{ SUBSCRIPTIONS : "follows"
    USERS ||--o{ CART_ITEMS : "adds"
    USERS ||--o{ ORDERS : "places"
    USERS ||--o{ DEVICE_TOKENS : "registers"
    USERS ||--o{ AUDIT_LOG : "acts"

    CATEGORIES ||--o{ OFFERS : "groups"
    BUSINESSES ||--o{ OFFERS : "publishes"
    BUSINESSES ||--o{ REVIEWS : "receives"
    BUSINESSES ||--o{ SUBSCRIPTIONS : "has"

    OFFERS ||--o{ COUPONS : "issues"
    OFFERS ||--o{ FAVORITES : "in"
    OFFERS ||--o{ CART_ITEMS : "in"
    OFFERS ||--o{ ORDER_ITEMS : "in"
    COUPONS ||--o{ USER_COUPONS : "claimed as"

    ORDERS ||--|{ ORDER_ITEMS : "contains"
    ORDERS ||--o{ PAYMENTS : "paid by"

    USERS {
        uuid id PK
        varchar email UK "lowercase"
        varchar password_hash "BCrypt"
        varchar full_name
        varchar role "CONSUMER | PROVIDER"
        boolean enabled
        timestamptz created_at
        timestamptz updated_at
    }
    REFRESH_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token_hash UK "SHA-256, never the raw token"
        uuid family_id "rotation chain"
        timestamptz expires_at
        timestamptz revoked_at
        timestamptz created_at
    }
    BUSINESSES {
        uuid id PK
        uuid owner_id FK, UK "one business per provider"
        varchar name
        text description
        varchar phone
        varchar address
        geography location "Point, SRID 4326"
        varchar logo_url
        timestamptz created_at
        timestamptz updated_at
    }
    CATEGORIES {
        smallint id PK
        varchar slug UK
        varchar name
    }
    OFFERS {
        uuid id PK
        uuid business_id FK
        smallint category_id FK
        varchar title
        text description
        varchar image_url
        numeric original_price
        numeric offer_price
        timestamptz starts_at
        timestamptz ends_at
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }
    COUPONS {
        uuid id PK
        uuid offer_id FK
        varchar title
        varchar discount_type "PERCENT | FIXED"
        numeric discount_value
        int stock "null = unlimited"
        int claimed_count
        int per_user_limit
        timestamptz valid_from
        timestamptz valid_until
        timestamptz created_at
    }
    USER_COUPONS {
        uuid id PK
        uuid coupon_id FK
        uuid user_id FK
        varchar code UK "shown as QR"
        varchar status "CLAIMED | REDEEMED | EXPIRED"
        timestamptz claimed_at
        timestamptz redeemed_at
        uuid redeemed_by FK "provider user"
    }
    FAVORITES {
        uuid user_id PK, FK
        uuid offer_id PK, FK
        timestamptz created_at
    }
    REVIEWS {
        uuid id PK
        uuid business_id FK
        uuid user_id FK
        smallint rating "1..5"
        text comment
        timestamptz created_at
    }
    SUBSCRIPTIONS {
        uuid user_id PK, FK
        uuid business_id PK, FK
        timestamptz created_at
    }
    CART_ITEMS {
        uuid user_id PK, FK
        uuid offer_id PK, FK
        int quantity
        timestamptz added_at
    }
    ORDERS {
        uuid id PK
        uuid user_id FK
        numeric total
        varchar currency "PEN"
        varchar status "PENDING | PAID | FAILED | CANCELLED"
        timestamptz created_at
    }
    ORDER_ITEMS {
        uuid order_id PK, FK
        uuid offer_id PK, FK
        int quantity
        numeric unit_price "price at purchase time"
    }
    PAYMENTS {
        uuid id PK
        uuid order_id FK
        varchar provider "STRIPE | YAPE_SIMULATED"
        varchar provider_ref
        varchar idempotency_key UK
        numeric amount
        varchar status "PENDING | SUCCEEDED | FAILED"
        timestamptz created_at
    }
    DEVICE_TOKENS {
        uuid id PK
        uuid user_id FK
        varchar token UK "FCM"
        varchar platform "ANDROID | IOS"
        timestamptz updated_at
    }
    AUDIT_LOG {
        bigint id PK
        uuid actor_id FK "nullable for system"
        varchar action "e.g. COUPON_REDEEMED"
        varchar entity_type
        varchar entity_id
        jsonb metadata
        timestamptz created_at
    }
```

---

## Español

### Decisiones

- **UUID como clave primaria** en lo que se expone en la API: no revela cuántos registros hay ni permite adivinar IDs ajenos. `categories` y `audit_log` usan claves numéricas porque son internas.
- **`geography(Point, 4326)`** en `businesses.location`: `ST_DWithin` mide en metros reales sobre la Tierra, y un índice GiST mantiene rápida la búsqueda por radio. Las ofertas heredan la ubicación de su negocio.
- **Refresh tokens guardados como hash SHA-256**, nunca en texto plano. Cada uso genera uno nuevo (rotación); si se reutiliza uno ya rotado, se revoca toda su `family_id`, porque indica un robo de token.
- **Cupón vs. cupón reclamado:** `coupons` es la plantilla que crea el proveedor (stock, vigencia); `user_coupons` es el código único que recibe cada consumidor y que se canjea una sola vez.
- **`order_items.unit_price`** guarda el precio del momento de la compra, para que cambiar una oferta no altere pedidos pasados.
- **`payments.idempotency_key` único:** si la app reintenta un pago con la misma `Idempotency-Key`, la base de datos impide cobrar dos veces.
- **`audit_log`** registra acciones sensibles (canjes, pagos, cambios de rol) con `jsonb` para los detalles.
- **Fechas en `timestamptz`** (UTC en la base de datos; la app convierte a hora de Lima).

### Migraciones por sprint

| Migración | Contenido | Día / Sprint |
|---|---|---|
| `V1__enable_postgis` | Extensión PostGIS | Día 1 ✅ |
| `V2__identity` | `users`, `refresh_tokens` | Día 1 |
| `V3__businesses_offers` | `businesses`, `categories` (con datos), `offers` | Día 2 |
| `V4__coupons_audit` | `coupons`, `user_coupons`, `audit_log` | Día 3 |
| `V5+` | Favoritos, reseñas, suscripciones, carrito, pedidos, pagos, dispositivos | Después del MVP |

Una migración aplicada nunca se edita: cualquier cambio va en una migración nueva.

---

## English

### Decisions

- **UUID primary keys** for everything exposed by the API: they hide record counts and prevent guessing other users' IDs. `categories` and `audit_log` use numeric keys because they are internal.
- **`geography(Point, 4326)`** for `businesses.location`: `ST_DWithin` measures real meters on the Earth, and a GiST index keeps radius search fast. Offers inherit their business location.
- **Refresh tokens stored as SHA-256 hashes**, never in plain text. Each use issues a new one (rotation); reusing an already-rotated token revokes its whole `family_id`, since it signals token theft.
- **Coupon vs. claimed coupon:** `coupons` is the provider's template (stock, validity); `user_coupons` is the unique code each consumer gets, redeemable once.
- **`order_items.unit_price`** stores the price at purchase time, so editing an offer never changes past orders.
- **Unique `payments.idempotency_key`:** if the app retries a payment with the same `Idempotency-Key`, the database prevents a double charge.
- **`audit_log`** records sensitive actions (redemptions, payments, role changes) with `jsonb` details.
- **`timestamptz` everywhere** (UTC in the database; the app converts to Lima time).

### Migrations per sprint

| Migration | Content | Day / Sprint |
|---|---|---|
| `V1__enable_postgis` | PostGIS extension | Day 1 ✅ |
| `V2__identity` | `users`, `refresh_tokens` | Day 1 |
| `V3__businesses_offers` | `businesses`, `categories` (seeded), `offers` | Day 2 |
| `V4__coupons_audit` | `coupons`, `user_coupons`, `audit_log` | Day 3 |
| `V5+` | Favorites, reviews, subscriptions, cart, orders, payments, devices | After the MVP |

An applied migration is never edited: any change goes into a new migration.

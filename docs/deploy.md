# Despliegue / Deployment

[Español](#español) · [English](#english)

---

## Español

La API corre en **Render** (contenedor Docker + Redis) y la base de datos en **Neon** (PostgreSQL con PostGIS). Ambos tienen plan gratuito.

```
App móvil ──HTTPS──▶ Render: geops-api (Docker) ──▶ Neon: PostgreSQL + PostGIS
                                │
                                └──▶ Render: geops-redis (red privada)
```

### 1. Base de datos en Neon

1. Crea una cuenta en [neon.tech](https://neon.tech) y un proyecto llamado `geops`.
2. Región: **AWS us-east-1 (N. Virginia)**, la misma zona que el servicio de Render.
3. En **Connection details**, desactiva **Connection pooling**: Flyway necesita una conexión directa para las migraciones.
4. Copia la cadena de conexión. Tiene esta forma:
   `postgresql://USUARIO:CONTRASEÑA@HOST/neondb?sslmode=require`
5. Sepárala en tres valores para Render:

| Variable | Valor |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/neondb?sslmode=require` |
| `DB_USERNAME` | `USUARIO` |
| `DB_PASSWORD` | `CONTRASEÑA` |

No hace falta activar PostGIS a mano: la migración `V1` ejecuta `CREATE EXTENSION postgis`.

### 2. API y Redis en Render

1. Crea una cuenta en [render.com](https://render.com) y conecta tu GitHub con acceso a la organización `geops-app`.
2. **New → Blueprint** y elige el repositorio `geops-app/geops-api`. Render lee `render.yaml` y propone dos servicios: `geops-api` y `geops-redis`.
3. Cuando lo pida, pega `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` del paso 1. **Escríbelos solo en Render**, nunca en el repositorio ni en el chat.
4. `JWT_SECRET` lo genera Render automáticamente; `REDIS_URL` lo conecta solo.
5. Aplica el Blueprint. El primer despliegue compila la imagen (unos minutos) y Flyway crea las tablas.

### 3. Comprobar

- `https://geops-api.onrender.com/actuator/health` → `{"status":"UP"}`
- `https://geops-api.onrender.com/swagger-ui.html` → documentación interactiva.

Si el nombre `geops-api` ya está tomado en Render, la URL cambia: actualiza `CORS_ALLOWED_ORIGINS` en Render y la URL de la app.

### 4. App móvil apuntando a producción

Crea un archivo `.env.prod` (está en `.gitignore`) en `geops-mobile`:

```
APP_ENV=prod
API_BASE_URL=https://geops-api.onrender.com/api/v1
```

Y compila con:

```bash
flutter build apk --release --dart-define-from-file=.env.prod
```

### Cómo funciona después

- **Despliegue continuo:** cada merge a `main` (es decir, cada release) se despliega solo, pero **solo si el CI de GitHub pasa** (`autoDeployTrigger: checksPass`).
- **Plan gratuito:** el servicio se duerme tras 15 minutos sin uso; la primera petición después tarda ~1 minuto en despertar. Neon también suspende la base de datos y la reactiva en segundos.
- **Redis gratuito no persiste datos:** solo guarda caché y contadores de intentos de login, así que perderlos no rompe nada.
- **Secretos:** solo viven en Render. Para rotar `JWT_SECRET`, cámbialo en Render: todas las sesiones tendrán que iniciar de nuevo.

---

## English

The API runs on **Render** (Docker container + Redis) and the database on **Neon** (PostgreSQL with PostGIS). Both have free plans.

### 1. Database on Neon

1. Create a [neon.tech](https://neon.tech) account and a project named `geops`.
2. Region: **AWS us-east-1 (N. Virginia)**, the same area as the Render service.
3. In **Connection details**, turn off **Connection pooling**: Flyway needs a direct connection for migrations.
4. Copy the connection string, shaped like `postgresql://USER:PASSWORD@HOST/neondb?sslmode=require`.
5. Split it into three values for Render:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:postgresql://HOST/neondb?sslmode=require` |
| `DB_USERNAME` | `USER` |
| `DB_PASSWORD` | `PASSWORD` |

PostGIS does not need to be enabled by hand: migration `V1` runs `CREATE EXTENSION postgis`.

### 2. API and Redis on Render

1. Create a [render.com](https://render.com) account and connect GitHub with access to the `geops-app` organization.
2. **New → Blueprint**, pick `geops-app/geops-api`. Render reads `render.yaml` and proposes `geops-api` and `geops-redis`.
3. When asked, paste `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. **Enter them only in Render**, never in the repository.
4. Render generates `JWT_SECRET` and wires `REDIS_URL` by itself.
5. Apply the Blueprint. The first deploy builds the image and Flyway creates the tables.

### 3. Check

- `https://geops-api.onrender.com/actuator/health` → `{"status":"UP"}`
- `https://geops-api.onrender.com/swagger-ui.html` → interactive docs.

If the `geops-api` name is taken on Render, the URL changes: update `CORS_ALLOWED_ORIGINS` and the app's URL.

### 4. Mobile app pointing to production

Create `.env.prod` (gitignored) in `geops-mobile` with `API_BASE_URL=https://geops-api.onrender.com/api/v1` and build with `flutter build apk --release --dart-define-from-file=.env.prod`.

### Afterwards

- **Continuous deployment:** every merge to `main` (every release) deploys, **only after GitHub CI passes**.
- **Free plan:** the service sleeps after 15 idle minutes; the next request takes about a minute. Neon suspends and resumes in seconds.
- **Free Redis is not persistent:** it only holds cache and login-attempt counters.
- **Secrets** live only in Render. Rotating `JWT_SECRET` signs everyone out.

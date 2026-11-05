-- Businesses, categories and offers.

CREATE TABLE categories (
    id   smallint    PRIMARY KEY,
    slug varchar(40) NOT NULL UNIQUE,
    name varchar(60) NOT NULL
);

INSERT INTO categories (id, slug, name) VALUES
    (1, 'food',          'Comida y restaurantes'),
    (2, 'groceries',     'Bodegas y mercados'),
    (3, 'bakery',        'Panaderías y cafés'),
    (4, 'health',        'Salud y farmacia'),
    (5, 'beauty',        'Belleza y cuidado personal'),
    (6, 'fashion',       'Moda'),
    (7, 'tech',          'Tecnología'),
    (8, 'services',      'Servicios'),
    (9, 'entertainment', 'Entretenimiento');

-- One business per provider. The app writes latitude/longitude; PostgreSQL derives
-- the PostGIS point, so the application never handles spatial types directly.
CREATE TABLE businesses (
    id          uuid          PRIMARY KEY,
    owner_id    uuid          NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    name        varchar(120)  NOT NULL,
    description varchar(1000),
    phone       varchar(20),
    address     varchar(200)  NOT NULL,
    latitude    double precision NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude   double precision NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    location    geography(Point, 4326) GENERATED ALWAYS AS (
                    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                ) STORED,
    logo_url    varchar(500),
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL
);

-- Makes ST_DWithin radius searches use an index instead of scanning every business.
CREATE INDEX ix_businesses_location ON businesses USING GIST (location);

CREATE TABLE offers (
    id             uuid          PRIMARY KEY,
    business_id    uuid          NOT NULL REFERENCES businesses (id) ON DELETE CASCADE,
    category_id    smallint      NOT NULL REFERENCES categories (id),
    title          varchar(120)  NOT NULL,
    description    varchar(1000),
    image_url      varchar(500),
    original_price numeric(10, 2) CHECK (original_price >= 0),
    offer_price    numeric(10, 2) NOT NULL CHECK (offer_price >= 0),
    starts_at      timestamptz   NOT NULL,
    ends_at        timestamptz   NOT NULL,
    active         boolean       NOT NULL DEFAULT true,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    CONSTRAINT ck_offers_period CHECK (ends_at > starts_at),
    CONSTRAINT ck_offers_discount CHECK (original_price IS NULL OR original_price >= offer_price)
);

CREATE INDEX ix_offers_business ON offers (business_id);
CREATE INDEX ix_offers_active_period ON offers (active, ends_at);

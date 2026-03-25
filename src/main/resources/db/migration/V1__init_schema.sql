-- =============================================================
-- SwiftCart — Initial Schema
-- V1__init_schema.sql
--
-- Design notes:
--   • All PKs use BIGSERIAL (auto-increment) for simplicity and
--     join performance on OLTP workloads.
--   • inventory.version enables JPA optimistic locking (@Version)
--     to prevent overselling without holding long DB locks.
--   • order_items snapshot unit_price at time of purchase so that
--     later price changes don't affect historical orders.
-- =============================================================

-- ---------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ---------------------------------------------------------------
-- CATEGORIES
-- ---------------------------------------------------------------
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    parent_id   BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- PRODUCTS
-- ---------------------------------------------------------------
CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255)   NOT NULL,
    slug         VARCHAR(300)   NOT NULL UNIQUE,
    description  TEXT,
    price        NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    category_id  BIGINT         REFERENCES categories(id) ON DELETE SET NULL,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active    ON products(active);

-- ---------------------------------------------------------------
-- INVENTORY
-- Separate table from products to allow dedicated locking without
-- touching the main product row on every stock update.
-- ---------------------------------------------------------------
CREATE TABLE inventory (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT  NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
    quantity    INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version     BIGINT  NOT NULL DEFAULT 0,   -- optimistic lock version
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_product ON inventory(product_id);

-- ---------------------------------------------------------------
-- CARTS
-- One active cart per user. A cart is soft-deleted (or cleared)
-- after a successful order.
-- ---------------------------------------------------------------
CREATE TABLE carts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT    NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE cart_items (
    id          BIGSERIAL PRIMARY KEY,
    cart_id     BIGINT         NOT NULL REFERENCES carts(id)    ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity    INTEGER        NOT NULL DEFAULT 1 CHECK (quantity > 0),
    unit_price  NUMERIC(12, 2) NOT NULL,   -- price snapshotted when added to cart
    UNIQUE (cart_id, product_id)
);

-- ---------------------------------------------------------------
-- ORDERS
-- ---------------------------------------------------------------
CREATE TABLE orders (
    id             BIGSERIAL      PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users(id),
    status         VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    total_amount   NUMERIC(12, 2) NOT NULL,
    shipping_address TEXT,
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user   ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id          BIGSERIAL      PRIMARY KEY,
    order_id    BIGINT         NOT NULL REFERENCES orders(id)   ON DELETE CASCADE,
    product_id  BIGINT         NOT NULL REFERENCES products(id),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(12, 2) NOT NULL   -- price locked at time of order
);

CREATE INDEX idx_order_items_order ON order_items(order_id);

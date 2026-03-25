-- =============================================================
-- SwiftCart — Seed Data (development only)
-- V2__seed_data.sql
-- =============================================================

-- Categories
INSERT INTO categories (name, slug) VALUES
    ('Electronics',   'electronics'),
    ('Clothing',      'clothing'),
    ('Home & Garden', 'home-garden'),
    ('Books',         'books'),
    ('Sports',        'sports');

INSERT INTO categories (name, slug, parent_id) VALUES
    ('Smartphones',   'smartphones',   1),
    ('Laptops',       'laptops',       1),
    ('Men''s Wear',   'mens-wear',     2),
    ('Women''s Wear', 'womens-wear',   2);

-- Products
INSERT INTO products (name, slug, description, price, category_id) VALUES
    ('Samsung Galaxy S24',       'samsung-galaxy-s24',       'Latest Samsung flagship',        18999.00, 6),
    ('Apple iPhone 15',          'apple-iphone-15',          'Apple flagship smartphone',      22999.00, 6),
    ('Dell XPS 15',              'dell-xps-15',              'Premium ultrabook',              28999.00, 7),
    ('Levi''s 501 Jeans',        'levis-501-jeans',          'Classic straight-leg jeans',      1299.00, 8),
    ('Nike Air Max',             'nike-air-max',             'Iconic running shoe',             2499.00, 5),
    ('The Pragmatic Programmer', 'pragmatic-programmer-book','Essential dev reading',            599.00, 4);

-- Inventory (start all products with 100 units)
INSERT INTO inventory (product_id, quantity) VALUES
    (1, 100),
    (2, 100),
    (3, 50),
    (4, 200),
    (5, 150),
    (6, 80);

-- Admin user (password: Admin@123 — bcrypt hashed)
INSERT INTO users (email, password_hash, first_name, last_name, role) VALUES
    ('admin@swiftcart.co.za',
     '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQyCgFrxmBeFPxIuR.WB.XTKi',
     'Admin', 'SwiftCart', 'ADMIN');

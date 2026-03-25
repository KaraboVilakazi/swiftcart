# 🛒 SwiftCart

> Production-quality e-commerce backend built with Java 17, Spring Boot, PostgreSQL, Redis and Docker.

![Java](https://img.shields.io/badge/Java-17-orange?logo=java) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?logo=springboot) ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql) ![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis) ![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker) ![License](https://img.shields.io/badge/license-MIT-lightgrey)

---

## 📖 About

SwiftCart is a modular, scalable e-commerce backend API demonstrating enterprise-grade software engineering principles. It handles the full shopping lifecycle — browsing products, managing a cart, placing orders, and tracking inventory — with production concerns like concurrency control, caching, event-driven design, and failure handling built in from the start.

---

## ✅ Live API Response

```json
{
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "Samsung Galaxy S24",
        "slug": "samsung-galaxy-s24",
        "price": 18999,
        "categoryName": "Smartphones",
        "active": true
      }
    ],
    "totalElements": 6,
    "totalPages": 1
  }
}
```

---

## 🏗️ Architecture

```
src/main/java/com/swiftcart/
├── config/               JWT filter, Spring Security, Redis config
├── common/               BaseEntity, ApiResponse envelope, exceptions
├── user/                 Registration, login, JWT auth, profile
├── product/              Product CRUD, categories, Redis caching
├── inventory/            Stock tracking, optimistic locking
├── cart/                 Cart management, price snapshotting
└── order/                Order placement, rollback, event publishing
```

### Layered Design (per module)

```
Controller  →  Service  →  Repository  →  Database
   (API)    (Business)    (Data Access)
```

---

## ⚙️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JWT (JJWT) |
| Database | PostgreSQL 16 |
| Caching | Redis 7 |
| Migrations | Flyway |
| Containerisation | Docker + Docker Compose |
| Build | Maven |

---

## 🚀 Getting Started

### Prerequisites
- Docker Desktop

### Run

```bash
git clone https://github.com/KaraboVilakazi/swiftcart.git
cd swiftcart
docker compose up --build
```

API is available at **`http://localhost:8081`**

> PostgreSQL runs on `localhost:5433` and Redis on `localhost:6379`

---

## 📡 API Reference

### Auth

```bash
# Register
POST /api/v1/auth/register
{
  "email": "you@example.com",
  "password": "Password1",
  "firstName": "Karabo",
  "lastName": "V"
}

# Login → returns JWT token
POST /api/v1/auth/login
{
  "email": "you@example.com",
  "password": "Password1"
}
```

### Products *(public)*

```bash
GET  /api/v1/products                    # paginated list
GET  /api/v1/products/{id}               # single product (Redis cached)
GET  /api/v1/products/search?q=samsung   # search by name
GET  /api/v1/products/category/{id}      # filter by category
```

### Cart *(requires JWT)*

```bash
GET    /api/v1/cart
POST   /api/v1/cart/items?productId=1&quantity=2
PUT    /api/v1/cart/items/{productId}?quantity=3
DELETE /api/v1/cart/items/{productId}
```

### Orders *(requires JWT)*

```bash
POST /api/v1/orders
{ "shippingAddress": "123 Sandton Drive, Johannesburg" }

GET  /api/v1/orders          # order history
GET  /api/v1/orders/{id}     # order detail
```

### Inventory *(admin)*

```bash
GET  /api/v1/inventory/{productId}
POST /api/v1/inventory/{productId}/restock?quantity=50
```

All responses follow the same envelope:

```json
{ "success": true, "message": "...", "data": { ... }, "timestamp": "..." }
```

---

## 🔐 Authentication

Include the JWT token from login in the `Authorization` header:

```
Authorization: Bearer <your_token>
```

A seeded admin account is available:
- **Email:** `admin@swiftcart.co.za`
- **Password:** `Admin@123`

---

## 🧠 Engineering Decisions

### Concurrency & Overselling Prevention
The `Inventory` entity uses JPA `@Version` (optimistic locking). When two users order the same product simultaneously, the second writer receives an `OptimisticLockException`. The service retries up to 3 times before returning a `409 Conflict`. No DB row locks are held between retries, keeping throughput high.

```
Thread A: reads qty=10, version=5  →  UPDATE ... WHERE version=5  ✅ succeeds
Thread B: reads qty=10, version=5  →  UPDATE ... WHERE version=5  ❌ 0 rows → retry
```

### Order Placement & Rollback
Stock is deducted **before** the order is persisted. If any line item fails (insufficient stock, DB error), all previously deducted items are rolled back explicitly. This prevents confirmed orders with no reserved stock.

### Event-Driven Design
`OrderService` publishes an `OrderCreatedEvent` via Spring's `ApplicationEventPublisher`. Listeners handle notifications and dispatch queuing asynchronously (`@Async`), so the API response returns immediately. The publisher can be swapped to Kafka/RabbitMQ without touching `OrderService`.

### Redis Caching
Individual products (`/products/{id}`) are cached with a 10-minute TTL. Cache is evicted on any product write. Paginated listings are intentionally **not** cached — `PageImpl` is not safely serialisable in Redis and the combinatorial key space makes it impractical.

### Flyway Migrations
`spring.jpa.hibernate.ddl-auto: validate` — Flyway owns the schema, JPA only validates against it. This prevents silent schema drift in production.

### Price Snapshotting
`CartItem.unitPrice` and `OrderItem.unitPrice` are snapshotted at the moment of action. Price changes to a product never retroactively affect carts or historical orders.

---

## 🗄️ Database Schema

```
users          — id, email, password_hash, role
categories     — id, name, slug, parent_id (self-referential)
products       — id, name, slug, price, category_id, active
inventory      — id, product_id, quantity, version (optimistic lock)
carts          — id, user_id
cart_items     — id, cart_id, product_id, quantity, unit_price
orders         — id, user_id, status, total_amount
order_items    — id, order_id, product_id, quantity, unit_price
```

---

## 📁 Project Structure

```
swiftcart/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── src/main/
    ├── resources/
    │   ├── application.yml
    │   └── db/migration/
    │       ├── V1__init_schema.sql
    │       └── V2__seed_data.sql
    └── java/com/swiftcart/
        ├── SwiftCartApplication.java
        ├── config/
        │   ├── JwtService.java
        │   ├── JwtAuthFilter.java
        │   ├── JwtProperties.java
        │   ├── SecurityConfig.java
        │   └── RedisConfig.java
        ├── common/
        │   ├── domain/BaseEntity.java
        │   ├── response/ApiResponse.java
        │   └── exception/
        ├── user/
        ├── product/
        ├── inventory/
        ├── cart/
        └── order/
```

---

## 📋 Requirements

- Docker Desktop (no local Java or Maven needed)

---

## 📄 License

MIT — build on it, extend it, ship it.

<div align="center">

# 🛒 SwiftCart

**Production-grade e-commerce backend — built for concurrency, built for scale.**

[![Java](https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.2-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org)
[![Redis](https://img.shields.io/badge/Redis_7-DC382D?style=for-the-badge&logo=redis&logoColor=white)](https://redis.io)
[![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com)
[![Railway](https://img.shields.io/badge/Deployed_on_Railway-0B0D0E?style=for-the-badge&logo=railway&logoColor=white)](https://railway.app)

*SwiftCart handles the full shopping lifecycle — product browsing, cart management, order placement, and inventory tracking — with production concerns baked in from day one.*

</div>

---

## ⚡ Engineering Highlights

### Optimistic Locking — No Overselling
Two users checkout the same last item simultaneously. SwiftCart handles this with JPA's `@Version` field — the second writer receives an `OptimisticLockException`, the service retries up to 3 times, and returns a conflict status if stock is gone. No race conditions, no overselling.

### Event-Driven Order Workflow
`OrderService` publishes an `OrderCreatedEvent` via Spring's `ApplicationEventPublisher`. Async listeners handle downstream concerns without blocking the order response. Stock is deducted before order persistence — if any item fails, previously deducted quantities are explicitly rolled back.

### Redis Caching with Intent
Individual product lookups cache with a 10-minute TTL. Paginated listings intentionally skip caching — too many key combinations, serialization overhead not worth it. Caching is applied where it helps, not everywhere.

### Price Snapshotting
Price changes to a product never retroactively affect existing carts or historical orders. Cart items store a `snapshotPrice` at time of add. Your order history is immutable.

---

## 🏗️ Architecture

```
src/main/java/com/swiftcart/
├── config/         # JWT, Security, Redis configuration
├── common/         # BaseEntity, ApiResponse wrappers
├── user/           # Registration, login, JWT issuance
├── product/        # CRUD, Redis caching layer
├── inventory/      # Stock tracking, optimistic lock handling
├── cart/           # Cart management, price snapshotting
└── order/          # Order placement, event publishing, rollback
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Auth | Spring Security + JWT (JJWT) |
| Migrations | Flyway |
| Containers | Docker & Docker Compose |
| Infra-as-code | Terraform |
| Deployment | Railway |

---

## 🚀 Running Locally

Only Docker Desktop required.

```bash
git clone https://github.com/KaraboVilakazi/swiftcart.git
cd swiftcart
docker compose up --build
```

API available at `http://localhost:8081`. A Postman collection is included for testing all endpoints.

---

## 📡 Key Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register a new user |
| `POST` | `/api/auth/login` | Authenticate, receive JWT |
| `GET` | `/api/products` | Browse products (paginated) |
| `GET` | `/api/products/{id}` | Get product (Redis cached) |
| `POST` | `/api/cart/add` | Add item to cart |
| `GET` | `/api/cart` | View cart |
| `POST` | `/api/orders` | Place order (with concurrency control) |
| `GET` | `/api/orders` | Order history |

---

## 📄 License

MIT

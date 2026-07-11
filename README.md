<div align="center">

# 📅 Bookly — Backend API

**Production-ready SaaS booking backend — multi-tenant, subdomain-isolated, JWT-secured.**  
**Staff scheduling, real-time slot availability & guest checkout. Java 21 · Spring Boot · PostgreSQL · Redis.**

[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.1-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat-square&logo=redis&logoColor=white)](https://redis.io/)
[![Maven](https://img.shields.io/badge/Maven-3.9-C71A36?style=flat-square&logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

[Prerequisites](#prerequisites) · [Getting Started](#getting-started) · [Configuration](#configuration) · [API Overview](#api-overview) · [Tech Stack](#tech-stack) · [Frontend →](https://github.com/akhand471/bookly-frontend)

</div>

---

## ✨ About

**Bookly** is a multi-tenant SaaS appointment booking platform for clinics and service businesses. Each business gets its own isolated tenant space, custom subdomain, and a fully branded patient-facing booking page — backed by a secure, battle-tested REST API.

> **Think:** A Calendly for clinics — owners manage staff, services & schedules; patients book without needing an account.

---

## 📋 Prerequisites

Before running the project, ensure you have the following installed:

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 21+ | [OpenJDK 21](https://openjdk.org/projects/jdk/21/) — project is compiled to Java 21 |
| **Maven** | 3.9+ | Or use the included `./mvnw` wrapper — no separate install needed |
| **PostgreSQL** | 16 | Database name: `bookly_db`, default port `5432` |
| **Redis** | 7+ | Default host: `localhost`, default port `6379` |
| **Git** | Any | For cloning the repository |

> **Optional:** Docker & Docker Compose for running PostgreSQL and Redis without local install.

---

## ⚡ Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/akhand471/bookly.git
cd bookly
```

### 2. Create the Database

```bash
psql -U postgres -c "CREATE DATABASE bookly_db;"
```

> Flyway will automatically create all tables and seed data on first startup.

### 3. Configure Environment Variables

```bash
cp .env.example .env
```

Open `.env` and fill in your values:

```env
# PostgreSQL
DB_URL=jdbc:postgresql://localhost:5432/bookly_db
DB_USERNAME=postgres
DB_PASSWORD=your_db_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT — generate with: openssl rand -base64 64
JWT_SECRET=your_64_byte_base64_secret
JWT_EXPIRATION_MS=900000         # 15 minutes
JWT_REFRESH_EXPIRATION_MS=604800000  # 7 days

# SMTP (Brevo / Sendinblue or any SMTP relay)
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=noreply@example.com
MAIL_PASSWORD=your_smtp_key
MAIL_FROM=noreply@example.com

# Google OAuth2 (optional)
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Production proxy (leave empty for local dev)
TRUSTED_PROXY_CIDR=
```

### 4. Run the Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The server starts at **http://localhost:8080**  
Swagger UI (interactive API docs): **http://localhost:8080/swagger-ui.html**  
Actuator health check: **http://localhost:8080/actuator/health**

### 5. Run Tests

```bash
./mvnw test
```

---

## 🔧 Dependencies

All dependencies are declared in [`pom.xml`](pom.xml). Below is a breakdown by category:

### Core Framework

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-parent` | **3.3.1** | BOM — manages all Spring dependency versions |
| `spring-boot-starter-web` | (managed) | Embedded Tomcat + Spring MVC REST layer |
| `spring-boot-starter-data-jpa` | (managed) | Hibernate 6.5 ORM + Spring Data repositories |
| `spring-boot-starter-validation` | (managed) | Jakarta Bean Validation (JSR-380) |
| `spring-boot-starter-actuator` | (managed) | Health, info, and metrics endpoints |

### Security & Auth

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-security` | (managed) | Spring Security 6 filter chain + method security |
| `spring-boot-starter-oauth2-client` | (managed) | Google OAuth2 social login |
| `jjwt-api` | **0.12.5** | JWT creation and verification API |
| `jjwt-impl` | **0.12.5** | JJWT implementation (runtime) |
| `jjwt-jackson` | **0.12.5** | JSON serialization for JWT (runtime) |
| `passay` | **1.6.4** | Password policy enforcement (length, complexity rules) |

### Database & Migrations

| Dependency | Version | Purpose |
|---|---|---|
| `postgresql` | (managed) | PostgreSQL JDBC driver (runtime) |
| `flyway-core` | (managed) | Database migration engine |
| `flyway-database-postgresql` | (managed) | PostgreSQL-specific Flyway dialect |

### Cache & Rate Limiting

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-data-redis` | (managed) | Lettuce Redis client + Spring Data Redis |
| `bucket4j-core` | **8.10.1** | Token-bucket rate limiting algorithm |
| `bucket4j-redis` | **8.10.1** | Redis-backed distributed Bucket4j storage |

### Utilities

| Dependency | Version | Purpose |
|---|---|---|
| `mapstruct` | **1.5.5.Final** | Compile-time DTO ↔ Entity mapping code generation |
| `mapstruct-processor` | **1.5.5.Final** | Annotation processor for MapStruct (provided scope) |
| `lombok` | (managed) | Boilerplate reduction (`@Getter`, `@Builder`, etc.) |
| `lombok-mapstruct-binding` | **0.2.0** | Ensures correct Lombok + MapStruct annotation processor order |
| `ipaddress` | **5.5.0** | IPv4/IPv6 CIDR parsing for trusted-proxy resolution |
| `spring-boot-starter-mail` | (managed) | JavaMail / SMTP email sending |
| `springdoc-openapi-starter-webmvc-ui` | **2.5.0** | OpenAPI 3 + Swagger UI auto-generation |

### Testing

| Dependency | Version | Purpose |
|---|---|---|
| `spring-boot-starter-test` | (managed) | JUnit 5 + Mockito + AssertJ + Spring Test |
| `spring-security-test` | (managed) | `@WithMockUser`, `MockMvc` security helpers |

### Build Plugins

| Plugin | Purpose |
|---|---|
| `spring-boot-maven-plugin` | Creates executable fat JAR; excludes Lombok from the final artifact |
| `maven-compiler-plugin` | Configures Java 21 source/target; wires Lombok → MapStruct annotation processor order |
| `maven-surefire-plugin` | Disables JPMS module-path to prevent Bucket4j auto-module classloader split in tests |
| `build-helper-maven-plugin` | Registers `target/generated-sources/annotations` so IDEs resolve MapStruct generated code |

---

## ⚙️ Configuration

All settings are in [`src/main/resources/application.yml`](src/main/resources/application.yml). Every sensitive value is driven by an environment variable with a safe local default:

```yaml
# Database
spring.datasource.url:        ${DB_URL:jdbc:postgresql://localhost:5432/bookly_db}
spring.datasource.username:   ${DB_USERNAME:postgres}
spring.datasource.password:   ${DB_PASSWORD:}

# Redis
spring.data.redis.host:       ${REDIS_HOST:localhost}
spring.data.redis.port:       ${REDIS_PORT:6379}

# JWT
app.jwt.secret:               ${JWT_SECRET:<do-not-use-default-in-prod>}
app.jwt.expiration-ms:        900000      # 15 minutes
app.jwt.refresh-expiration-ms: 604800000  # 7 days

# Rate Limiting
app.rate-limit.login.max-attempts:    5    # per 15 min window
app.rate-limit.register.max-attempts: 3    # per 10 min window

# CORS
app.cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}

# Email Reminders Cron
app.scheduling.reminder-cron: "0 0 * * * *"   # every hour
```

> ⚠️ **Never commit a real `JWT_SECRET` to version control.** Generate one with:
> ```bash
> openssl rand -base64 64
> ```

---

## 📡 API Overview

All authenticated endpoints require:
```
Authorization: Bearer <access_token>
```

| Method | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Register a new business + owner account | Public |
| `POST` | `/api/v1/auth/login` | Login — returns `accessToken` + `refreshToken` | Public |
| `POST` | `/api/v1/auth/refresh` | Exchange refresh token for new token pair | Public |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token for current device | JWT |
| `GET` | `/api/v1/auth/me` | Get current authenticated user profile | JWT |
| `POST` | `/api/v1/auth/forgot-password` | Send password reset email | Public |
| `POST` | `/api/v1/auth/reset-password` | Reset password with token | Public |
| `POST` | `/api/v1/invitations` | Invite a staff member (sends email) | Owner |
| `POST` | `/api/v1/invitations/accept` | Accept invitation and set password | Public |
| `GET` | `/api/v1/staff` | List all staff in current tenant | JWT |
| `GET` | `/api/v1/staff/{id}` | Get staff member details | JWT |
| `GET` | `/api/v1/staff/{id}/schedule` | Get staff weekly recurring schedule | JWT |
| `PUT` | `/api/v1/staff/{id}/schedule` | Set staff weekly schedule | Owner |
| `GET` | `/api/v1/services` | List all bookable services | JWT |
| `POST` | `/api/v1/services` | Create a new service | Owner |
| `PUT` | `/api/v1/services/{id}` | Update a service | Owner |
| `DELETE` | `/api/v1/services/{id}` | Delete a service | Owner |
| `GET` | `/api/v1/appointments` | List appointments (paginated + filtered) | JWT |
| `POST` | `/api/v1/appointments` | Book a new appointment | JWT |
| `PATCH` | `/api/v1/appointments/{id}/status` | Update appointment status | JWT |
| `GET` | `/api/v1/availability` | Get available slots for a staff + date | JWT |
| `GET` | `/api/v1/customers` | List customers for current tenant | JWT |
| `GET` | `/api/v1/public/{subdomain}/services` | Public service listing (no auth) | Public |
| `GET` | `/api/v1/public/{subdomain}/staff` | Public staff listing | Public |
| `GET` | `/api/v1/public/{subdomain}/availability` | Public slot availability | Public |
| `POST` | `/api/v1/public/{subdomain}/bookings` | Guest booking — no account needed | Public |

> 📖 Full interactive docs with request/response schemas: **http://localhost:8080/swagger-ui.html**

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│           React SPA (bookly-frontend)           │
│       Vite 5 · Axios · React Router v6          │
└────────────────────┬────────────────────────────┘
                     │ HTTP/REST + JWT Bearer
┌────────────────────▼────────────────────────────┐
│            Spring Boot 3.3.1 API                │
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │        Security Filter Chain            │    │
│  │  RateLimitingFilter → JwtAuthFilter     │    │
│  └─────────────────────────────────────────┘    │
│                                                 │
│  ┌──────────┐  ┌──────────┐  ┌─────────────┐   │
│  │  Auth &  │  │ Booking  │  │   Public    │   │
│  │  OAuth2  │  │  Engine  │  │  Booking    │   │
│  └──────────┘  └──────────┘  └─────────────┘   │
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │      Multi-Tenant Isolation Layer       │    │
│  │  TenantContext + JWT businessId claim   │    │
│  └─────────────────────────────────────────┘    │
└─────────┬─────────────────────────┬─────────────┘
          │                         │
 ┌────────▼──────┐        ┌─────────▼──────┐
 │  PostgreSQL   │        │     Redis       │
 │   bookly_db   │        │  Rate Buckets   │
 │  13 Flyway    │        │  + Session TTLs │
 │  migrations   │        └────────────────┘
 └───────────────┘
```

---

## 🔒 Security Design

| Mechanism | Detail |
|---|---|
| **JWT Access Token** | 15-min expiry, signed `HS384` |
| **Refresh Token** | 7-day per-device rotation, stored in DB |
| **Rate Limiting** | Atomic Bucket4j token-bucket via Redis — 5 logins/15 min · 3 registrations/10 min |
| **Multi-Tenancy Isolation** | Every repository query scoped by `businessId` from JWT — cross-tenant data access impossible |
| **Password Policy** | Passay: min 8 chars · uppercase · lowercase · digit · special character |
| **Proxy Trust** | `X-Forwarded-For` only trusted when source IP matches `TRUSTED_PROXY_CIDR` (empty = never trusted) |
| **Audit Log** | Immutable `audit_logs` table — written for every auth event, password reset, and admin action |
| **CORS** | Explicit origin allowlist via `CORS_ALLOWED_ORIGINS` — no wildcard + credentials |
| **DDL Safety** | `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never modifies schema in production |

---

## 📁 Project Structure

```
bookly/
├── src/
│   ├── main/
│   │   ├── java/com/bookly/
│   │   │   ├── config/          # SecurityConfig, CorsConfig, AsyncConfig, SchedulerConfig
│   │   │   ├── controller/      # 10 REST controllers
│   │   │   ├── dto/             # 35 Request / Response DTO classes
│   │   │   ├── entity/          # JPA entities + PostgreSQL enums
│   │   │   ├── exception/       # GlobalExceptionHandler + custom exceptions
│   │   │   ├── mapper/          # MapStruct DTO ↔ Entity mappers
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   ├── security/        # JwtUtil, JwtAuthFilter, RateLimitingFilter, TenantContext
│   │   │   ├── service/         # Business logic layer (one service per domain)
│   │   │   └── validation/      # @StrongPassword, @NotReservedSubdomain validators
│   │   └── resources/
│   │       ├── application.yml         # Base config (env-variable driven)
│   │       ├── application-dev.yml     # Dev overrides (SQL logging on)
│   │       ├── application-prod.yml    # Prod overrides
│   │       └── db/migration/           # V1 → V13 Flyway SQL migrations
│   └── test/
│       └── java/com/bookly/            # JUnit 5 unit + integration tests
├── .env.example                        # Environment variable template
├── pom.xml                             # Maven build descriptor
└── README.md
```

---

## 🤝 Related Repositories

| Repo | Description |
|---|---|
| **[bookly-frontend](https://github.com/akhand471/bookly-frontend)** | React SPA — owner dashboard + public patient booking wizard |
| **[bookly](https://github.com/akhand471/bookly)** | This repository — Spring Boot REST API backend |

---

<div align="center">

Built with ❤️ using Spring Boot 3 · PostgreSQL 16 · Redis 7 · Flyway · MapStruct

</div>

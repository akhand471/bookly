# Bookly

Multi-tenant appointment booking SaaS platform built with Spring Boot 3, PostgreSQL, and Redis.

## Architecture

| Layer | Technology |
|-------|-----------|
| Runtime | Java 21, Spring Boot 3 |
| Database | PostgreSQL 16, Flyway migrations |
| Cache / Sessions | Redis 7 |
| Auth | JWT (HMAC-SHA) + Google OAuth2 |
| API Docs | OpenAPI 3 / Swagger UI |
| Containerization | Docker, Docker Compose |
| CI | GitHub Actions |

## Quick Start

### Prerequisites
- Java 21+
- Docker & Docker Compose

### Run with Docker Compose (recommended)

```bash
# Generate a JWT secret
export JWT_SECRET=$(openssl rand -base64 64)

# Start all services (Postgres, Redis, App)
docker compose up -d
```

The API will be available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`.

### Run for Development

```bash
# Start infrastructure only
docker compose up -d postgres redis

# Run the app with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## API Endpoints

### Authentication (`/api/v1/auth`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/register` | Register business + owner | Public |
| POST | `/login` | Authenticate credentials | Public |
| POST | `/refresh` | Refresh access token | Public |
| POST | `/logout` | Revoke current session | Bearer |
| POST | `/logout-all` | Revoke all sessions | Bearer |
| POST | `/forgot-password` | Request password reset | Public |
| POST | `/reset-password` | Reset password with token | Public |
| GET | `/me` | Current user profile | Bearer |

### Invitations (`/api/v1/invitations`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Invite employee | BUSINESS_OWNER |
| POST | `/accept` | Accept invitation | Public |

### OAuth2
| Flow | URL |
|------|-----|
| Google Login | `/oauth2/authorization/google` |

## Security Features

- **Rate Limiting** — Redis-backed sliding window (5 login / 3 register attempts)
- **Tenant Isolation** — Hibernate filters scope all queries by `business_id`
- **JWT Fail-Fast** — App refuses to start in production with default secret
- **Password Policy** — Min 8 chars, uppercase, lowercase, digit, special char
- **Audit Logging** — Immutable `audit_logs` table with JSONB details
- **Multi-device Sessions** — Per-device refresh tokens with `X-Device-Id` header

## Configuration

Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bookly_db` | Database URL |
| `DB_USERNAME` | — | Database user |
| `DB_PASSWORD` | — | Database password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `JWT_SECRET` | dev default | **Required in production** |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Access token TTL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins |
| `GOOGLE_CLIENT_ID` | — | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | — | Google OAuth2 client secret |

## Project Structure

```
src/main/java/com/bookly/
├── config/          # Security, Jackson, OpenAPI, WebMvc, Rate Limit
├── controller/      # REST controllers
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities
├── exception/       # Custom exceptions + global handler
├── repository/      # Spring Data JPA repositories
├── security/        # JWT, OAuth2, tenant context, rate limiting
├── service/         # Business logic
└── validation/      # Custom JSR-303 validators
```

## License

Proprietary — All rights reserved.

# Bookly

Multi-tenant appointment booking SaaS platform built with Spring Boot 3, PostgreSQL, and Redis.

## Architecture

| Layer | Technology |
|---|---|
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
- A local PostgreSQL & Redis instance (if running outside Docker)

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

### 1. Authentication & Users (`/api/v1/auth`)
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

### 2. Invitations (`/api/v1/invitations`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Invite employee | BUSINESS_OWNER |
| POST | `/accept` | Accept invitation | Public |

### 3. Services Management (`/api/v1/services`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Create a service | BUSINESS_OWNER |
| PUT | `/{id}` | Update service details | BUSINESS_OWNER |
| DELETE | `/{id}` | Soft delete a service | BUSINESS_OWNER |
| GET | `/` | List all business services | Bearer (Staff/Owner) |

### 4. Staff Schedules (`/api/v1/staff/schedules`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| PUT | `/{staffId}` | Set weekly schedule / working hours | BUSINESS_OWNER |
| GET | `/{staffId}` | Get weekly schedule | Bearer (Staff/Owner) |
| POST | `/{staffId}/overrides` | Create schedule override / day off | BUSINESS_OWNER |
| DELETE | `/{staffId}/overrides/{id}` | Delete schedule override | BUSINESS_OWNER |

### 5. Appointments (`/api/v1/appointments`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| POST | `/` | Create booking (staff on behalf of client) | Bearer (Staff/Owner) |
| GET | `/` | Query/Filter appointments (pageable) | Bearer (Staff/Owner) |
| GET | `/{id}` | Get appointment details | Bearer (Staff/Owner) |
| PUT | `/{id}/cancel` | Cancel booking | Bearer (Staff/Owner) |
| PUT | `/{id}/reschedule` | Reschedule booking | Bearer (Staff/Owner) |

### 6. Public Booking Flow (`/api/v1/public/{subdomain}`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/services` | List active services + average rating | Public |
| GET | `/staff` | List active staff + average rating | Public |
| GET | `/availability` | Fetch available slots for service/date | Public |
| POST | `/bookings` | Book guest appointment (sends email) | Public |
| GET | `/bookings/{id}` | Get booking details (acts as access token) | Public |
| POST | `/bookings/{id}/cancel` | Cancel guest booking (notice window checked) | Public |
| POST | `/bookings/{id}/reviews` | Submit appointment review rating (1-5) | Public |
| GET | `/services/{id}/reviews` | View public service reviews (masked names) | Public |
| GET | `/staff/{id}/reviews` | View public staff reviews (masked names) | Public |

### 7. Customers (`/api/v1/customers`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/` | List all customer profiles (pageable) | Bearer (Staff/Owner) |
| GET | `/{id}` | Get customer profile details | Bearer (Staff/Owner) |
| PUT | `/{id}` | Update customer details | Bearer (Staff/Owner) |
| GET | `/{id}/appointments` | Get customer booking history | Bearer (Staff/Owner) |

### 8. Reviews Administration (`/api/v1/reviews`)
| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/` | List all customer reviews (pageable) | Bearer (Staff/Owner) |

---

## Security Features

- **Rate Limiting** — Redis-backed sliding window (5 login / 3 register attempts)
- **Tenant Isolation** — Hibernate filters scope all queries dynamically by `business_id`
- **JWT Fail-Fast** — App refuses to start in production with default committed keys
- **Password Policy** — Enforced 8+ character strong password policy
- **Audit Logging** — Immutable `audit_logs` table capturing security events asynchronously
- **Multi-device Sessions** — Per-device refresh tokens with rotation and revocations
- **Cancellation Policy Window** — Rejects cancellations requested past the business deadline
- **Notification Log Idempotency** — Prevents duplicate email notifications or double-reminders

## Configuration

Key environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/bookly_db` | Database URL |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | — | Database password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `JWT_SECRET` | _(unset)_ | **Required in production** |
| `JWT_EXPIRATION_MS` | `900000` (15 min) | Access token TTL |
| `JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 days) | Refresh token TTL |
| `MAIL_HOST` | `smtp-relay.brevo.com` | SMTP Server Host |
| `MAIL_PORT` | `587` | SMTP Server Port |
| `MAIL_USERNAME` | `noreply@example.com` | SMTP Login Username |
| `MAIL_PASSWORD` | — | SMTP Login Password (key) |
| `MAIL_FROM` | `noreply@example.com` | Verified Sender Email |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated origins |
| `GOOGLE_CLIENT_ID` | — | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | — | Google OAuth2 client secret |
| `TRUSTED_PROXY_CIDR` | _(empty)_ | CIDR of upstream load balancer |

## Project Structure

```
src/main/java/com/bookly/
├── config/          # Security, Jackson, OpenAPI, WebMvc, Async
├── controller/      # REST controllers (Auth, Bookings, Customers, Reviews, Services, Schedules)
├── dto/             # Request/Response DTOs
├── entity/          # JPA entities (Business, User, Customer, Appointment, Review, NotificationLog)
├── exception/       # Custom exceptions + global handler
├── mapper/          # MapStruct DTO-Entity mappers
├── repository/      # Spring Data JPA repositories
├── security/        # JWT, OAuth2, tenant context, rate limiting
├── service/         # Business logic services
└── validation/      # Custom JSR-303 validators
```

## License

Proprietary — All rights reserved.

# Bookly — End-to-End Architecture & How It Works

> A production-grade, multi-tenant SaaS appointment booking platform built with Spring Boot 3, PostgreSQL, and Redis.

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Request Lifecycle](#4-request-lifecycle)
5. [Security Layers](#5-security-layers)
6. [Core User Flows](#6-core-user-flows)
   - [Business Registration](#61-business-registration)
   - [Login & JWT Authentication](#62-login--jwt-authentication)
   - [Token Refresh](#63-token-refresh)
   - [Multi-Tenant Isolation](#64-multi-tenant-isolation)
   - [Employee Invitation Flow](#65-employee-invitation-flow)
   - [Google OAuth2 Login](#66-google-oauth2-login)
   - [Password Reset Flow](#67-password-reset-flow)
7. [Database Schema](#7-database-schema)
8. [Rate Limiting & Brute Force Protection](#8-rate-limiting--brute-force-protection)
9. [Audit Logging](#9-audit-logging)
10. [Structured Logging](#10-structured-logging)
11. [Health Checks & Observability](#11-health-checks--observability)
12. [Containerization](#12-containerization)
13. [CI/CD Pipeline](#13-cicd-pipeline)
14. [API Documentation](#14-api-documentation)
15. [Configuration & Profiles](#15-configuration--profiles)
16. [What's Next to Build](#16-whats-next-to-build)

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     Client Applications                      │
│           (Web Browser / Mobile App / Postman)               │
└──────────────────────────┬───────────────────────────────────┘
                           │  HTTPS
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    Spring Boot API Server                    │
│                        (port 8080)                           │
│                                                              │
│   ┌─────────────────────────────────────────────────────┐   │
│   │              Spring Security Filter Chain            │   │
│   │                                                      │   │
│   │  1. RateLimitingFilter   ← Redis sliding window     │   │
│   │  2. JwtAuthenticationFilter ← Bearer token check    │   │
│   │  3. TenantInterceptor    ← Sets tenant context      │   │
│   │  4. SecurityConfig       ← Role-based route guards  │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │                REST Controllers                      │   │
│   │   AuthController · InvitationController             │   │
│   │   BusinessController · ActuatorController           │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │                  Service Layer                       │   │
│   │   AuthService · RefreshTokenService                 │   │
│   │   InvitationService · AuditService                  │   │
│   │   PasswordResetService · OAuth2LoginSuccessHandler  │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │               Repository Layer (JPA)                 │   │
│   │   BusinessRepository · UserRepository               │   │
│   │   RefreshTokenRepository · AuditLogRepository       │   │
│   └──────────────────────────┬──────────────────────────┘   │
└──────────────────────────────┼───────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              ▼                                 ▼
   ┌─────────────────┐               ┌─────────────────┐
   │   PostgreSQL    │               │      Redis      │
   │  (port 5432)   │               │   (port 6379)  │
   │                 │               │                 │
   │  - businesses   │               │  - rate limit   │
   │  - users        │               │    counters     │
   │  - audit_logs   │               │  - (future:     │
   │  - refresh_     │               │    sessions,    │
   │    tokens       │               │    cache)       │
   │  - invitations  │               └─────────────────┘
   └─────────────────┘
```

---

## 2. Technology Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.1 |
| Security | Spring Security + JWT | 6.x |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Database | PostgreSQL | 16 |
| Cache / Rate Limiting | Redis | 7 |
| DB Migrations | Flyway | 10.x |
| API Docs | SpringDoc OpenAPI (Swagger) | 2.x |
| OAuth2 | Spring Security OAuth2 Client | — |
| Token Library | JJWT (io.jsonwebtoken) | 0.12.x |
| Build Tool | Maven (Maven Wrapper) | 3.9.x |
| Containerization | Docker + Docker Compose | — |
| CI/CD | GitHub Actions | — |
| Logging | Logback (JSON in prod, console in dev) | — |

---

## 3. Project Structure

```
bookly/
├── src/
│   ├── main/
│   │   ├── java/com/bookly/
│   │   │   ├── BooklyApplication.java          # Entry point
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java         # Filter chain, route guards
│   │   │   │   ├── OpenApiConfig.java          # Swagger/OpenAPI setup
│   │   │   │   └── RedisConfig.java            # RedisTemplate setup
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java         # /api/v1/auth/*
│   │   │   │   └── InvitationController.java   # /api/v1/invitations/*
│   │   │   ├── dto/                            # Request/Response DTOs
│   │   │   ├── entity/                         # JPA Entities
│   │   │   │   ├── Business.java
│   │   │   │   ├── User.java
│   │   │   │   ├── RefreshToken.java
│   │   │   │   ├── AuditLog.java
│   │   │   │   ├── InvitationToken.java
│   │   │   │   └── PasswordResetToken.java
│   │   │   ├── exception/                      # Global exception handler
│   │   │   ├── repository/                     # Spring Data JPA repos
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── JwtService.java
│   │   │   │   ├── RateLimitingFilter.java
│   │   │   │   ├── TenantContext.java
│   │   │   │   ├── TenantInterceptor.java
│   │   │   │   ├── CustomUserDetailsService.java
│   │   │   │   └── OAuth2LoginSuccessHandler.java
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java
│   │   │   │   ├── RefreshTokenService.java
│   │   │   │   ├── InvitationService.java
│   │   │   │   ├── AuditService.java
│   │   │   │   └── PasswordResetService.java
│   │   │   └── validation/
│   │   │       ├── StrongPassword.java         # Custom annotation
│   │   │       └── StrongPasswordValidator.java
│   │   └── resources/
│   │       ├── application.yml                 # Base config
│   │       ├── application-dev.yml             # Dev overrides
│   │       ├── application-prod.yml            # Prod overrides
│   │       ├── logback-spring.xml              # Logging config
│   │       └── db/migration/                  # Flyway SQL scripts
│   │           ├── V1__create_businesses.sql
│   │           ├── V2__create_users.sql
│   │           ├── V3__create_refresh_tokens.sql
│   │           ├── V4__create_audit_logs.sql
│   │           └── V5__create_invitation_tokens.sql
│   └── test/
│       └── java/com/bookly/
│           ├── BooklyApplicationTests.java     # Context load test
│           ├── controller/AuthControllerTest.java
│           └── service/AuthServiceTest.java
├── Dockerfile                                  # Multi-stage Docker build
├── docker-compose.yml                          # App + Postgres + Redis
├── .github/workflows/ci.yml                   # GitHub Actions CI
├── docs/adr/                                  # Architecture Decision Records
│   └── ADR-001-api-versioning.md
├── ARCHITECTURE.md                            # This file
└── README.md
```

---

## 4. Request Lifecycle

Every HTTP request follows this exact sequence of steps:

```
Incoming Request
      │
      ▼
① RateLimitingFilter
   ├── Reads client IP from request
   ├── Checks Redis key: "rate:{ip}:{endpoint}"
   ├── If count > limit → 429 Too Many Requests (STOP)
   └── Else → increment counter, continue

      │
      ▼
② JwtAuthenticationFilter
   ├── Reads "Authorization: Bearer <token>" header
   ├── If no token → skip (public endpoints allowed through)
   ├── Validates JWT signature using secret key
   ├── Checks token expiry
   ├── Loads UserDetails from DB (CustomUserDetailsService)
   ├── Sets Authentication in SecurityContextHolder
   └── Continue

      │
      ▼
③ TenantInterceptor (Spring MVC HandlerInterceptor)
   ├── Reads "X-Tenant-ID" header OR extracts from subdomain
   ├── Sets TenantContext.setCurrentTenant(tenantId)  (thread-local)
   └── Continue

      │
      ▼
④ SecurityConfig (Spring Security Authorization)
   ├── Public routes: /auth/**, /actuator/health → PERMIT ALL
   ├── /actuator/** → requires SUPER_ADMIN
   ├── /api/v1/invitations → requires OWNER
   └── All other routes → requires authentication

      │
      ▼
⑤ Controller → Service → Repository
   ├── Business logic executes
   ├── JPA queries auto-filtered by tenant (Hibernate filter)
   └── Response serialized to JSON

      │
      ▼
⑥ AuditService (async @EventListener)
   └── Sensitive actions logged to audit_logs table asynchronously

      │
      ▼
Response sent to client
```

---

## 5. Security Layers

### Layer 1 — Rate Limiting (Redis)
Prevents brute-force attacks by tracking request counts in Redis with a sliding window.

```
Key format: "rate:{clientIp}:{endpoint}"
Example:    "rate:192.168.1.1:/api/v1/auth/login"

Rules:
  /login    → max 5 requests per 60 seconds
  /register → max 3 requests per 60 seconds
```

### Layer 2 — JWT Authentication
All protected endpoints require a valid JWT Bearer token.

```
Token Structure:
  Header:  { "alg": "HS256", "typ": "JWT" }
  Payload: { "sub": "user@email.com",
              "businessId": "uuid",
              "role": "OWNER",
              "iat": 1700000000,
              "exp": 1700000900 }   ← 15 min expiry
  Signature: HMAC-SHA256(header + payload, secretKey)
```

### Layer 3 — Multi-Tenant Isolation
Every DB query is automatically scoped to the current tenant.

```
TenantContext stores the businessId in a thread-local variable.
Hibernate @Filter applied to every entity:
  @Filter(name = "tenantFilter", condition = "business_id = :tenantId")
Result: Business A's data is PHYSICALLY UNREACHABLE from Business B's session.
```

### Layer 4 — Role-Based Access Control (RBAC)

| Role | Access |
|---|---|
| `SUPER_ADMIN` | Full access, including `/actuator/**` |
| `OWNER` | Manage their own business, staff, invitations |
| `STAFF` | Access only their own appointment data |

### Layer 5 — Password Policy (@StrongPassword)

```
Requirements enforced at registration & password reset:
  ✅ Minimum 8 characters
  ✅ At least 1 uppercase letter  (A-Z)
  ✅ At least 1 lowercase letter  (a-z)
  ✅ At least 1 digit             (0-9)
  ✅ At least 1 special character (!@#$%^&*...)
  ✅ Maximum 50 characters
```

### Layer 6 — Immutable Audit Logging
Every sensitive action is permanently recorded (no updates, no deletes).

```
Events logged:
  - USER_REGISTERED
  - USER_LOGIN
  - TOKEN_REFRESHED
  - PASSWORD_RESET_REQUESTED
  - PASSWORD_RESET_COMPLETED
  - INVITATION_CREATED
  - INVITATION_ACCEPTED
```

---

## 6. Core User Flows

### 6.1 Business Registration

```
Client                          API Server                    PostgreSQL
  │                                 │                              │
  │  POST /api/v1/auth/register     │                              │
  │  {                              │                              │
  │    businessName: "Barber Shop"  │                              │
  │    subdomain: "barber"          │                              │
  │    ownerFirstName: "Alex"       │                              │
  │    email: "alex@barber.com"     │                              │
  │    password: "Password123!"     │                              │
  │  }                              │                              │
  │ ───────────────────────────────►│                              │
  │                                 │  @StrongPassword validation  │
  │                                 │  Subdomain format check      │
  │                                 │  Reserved words check        │
  │                                 │  BCrypt hash password        │
  │                                 │  INSERT Business         ───►│
  │                                 │  INSERT User (OWNER)     ───►│
  │                                 │  Emit AUDIT event (async)    │
  │                                 │  Generate accessToken (JWT)  │
  │                                 │  Generate + store refresh ───►│
  │  200 OK                         │                              │
  │  { accessToken, refreshToken }  │                              │
  │◄───────────────────────────────│                              │
```

**Validation rules on `/register`:**
- `businessName` — not blank, max 100 chars
- `subdomain` — lowercase letters/numbers/hyphens only, 3-50 chars, not in reserved list (`www`, `api`, `admin`, `app`, `mail`, etc.)
- `email` — valid email format
- `password` — must pass `@StrongPassword` policy
- `ownerFirstName` / `ownerLastName` — not blank, max 50 chars

---

### 6.2 Login & JWT Authentication

```
Client                        API Server                  Redis     PostgreSQL
  │                               │                         │            │
  │  POST /api/v1/auth/login      │                         │            │
  │  { email, password }          │                         │            │
  │ ─────────────────────────────►│                         │            │
  │                               │  Check rate limit ─────►│            │
  │                               │  counter < 5 → OK ◄────│            │
  │                               │  Load user by email ─────────────── ►│
  │                               │  BCrypt.verify(password, hash)        │
  │                               │  Generate accessToken (15 min JWT)    │
  │                               │  Create + store refreshToken ────────►│
  │  200 OK                       │                         │            │
  │  { accessToken, refreshToken }│                         │            │
  │◄─────────────────────────────│                         │            │
  │                               │                         │            │
  │  GET /api/v1/auth/me          │                         │            │
  │  Authorization: Bearer eyJ...│                         │            │
  │ ─────────────────────────────►│                         │            │
  │                               │  Validate JWT signature              │
  │                               │  Check expiry                        │
  │                               │  Load user + set auth context        │
  │  200 OK { user data }         │                         │            │
  │◄─────────────────────────────│                         │            │
```

---

### 6.3 Token Refresh

The `accessToken` expires in **15 minutes**. To get a new one without re-logging in:

```
Client                          API Server                    PostgreSQL
  │                                 │                              │
  │  POST /api/v1/auth/refresh      │                              │
  │  { "refreshToken": "abc123" }   │                              │
  │ ───────────────────────────────►│                              │
  │                                 │  Find refreshToken in DB ───►│
  │                                 │  Check not expired           │
  │                                 │  Check not revoked           │
  │                                 │  DELETE old refreshToken ───►│
  │                                 │  Generate new accessToken    │
  │                                 │  INSERT new refreshToken ───►│
  │  200 OK                         │                              │
  │  { accessToken, refreshToken }  │                              │
  │◄───────────────────────────────│                              │
```

> **Security Note**: Refresh tokens are **rotated on every use** — old one deleted, new one issued.
> A stolen refresh token can only be used once before it's automatically invalidated.

---

### 6.4 Multi-Tenant Isolation

Bookly serves **multiple businesses** on one database with strict tenant isolation:

```
barber.bookly.com  →  X-Tenant-ID: "uuid-of-barber-shop"
salon.bookly.com   →  X-Tenant-ID: "uuid-of-salon"

                    PostgreSQL — users table
                    ┌──────────────────────────────────┐
                    │ id  │ email        │ business_id │
                    ├─────┼──────────────┼─────────────┤
                    │ 1   │ alex@...     │ uuid-barber │ ← Barber Shop
                    │ 2   │ jane@...     │ uuid-barber │ ← Barber Shop
                    │ 3   │ mike@...     │ uuid-salon  │ ← Salon (INVISIBLE to Barber)
                    └──────────────────────────────────┘
```

The Hibernate filter (`WHERE business_id = :tenantId`) is automatically applied to **every query**.
There is no way for one tenant to accidentally access another's data.

---

### 6.5 Employee Invitation Flow

```
Business Owner                  API Server                    New Employee
     │                               │                              │
     │  POST /api/v1/invitations     │                              │
     │  { email: "staff@..." }       │                              │
     │ ─────────────────────────────►│                              │
     │                               │  Generate secure token       │
     │                               │  BCrypt hash the token       │
     │                               │  Store InvitationToken in DB │
     │                               │  Send invite email ─────────────────────►
     │  201 Created                  │                              │
     │◄─────────────────────────────│                              │
     │                               │                              │
     │                               │   Employee clicks email link │
     │                               │◄─────────────────────────────
     │                               │  POST /api/v1/invitations/accept
     │                               │  { token, password }
     │                               │  Find non-expired token in DB│
     │                               │  Verify BCrypt hash          │
     │                               │  Create User (STAFF role)    │
     │                               │  Mark token as USED          │
     │                               │  Return JWT tokens ─────────────────────►
```

**Token security:**
- Raw token sent in email only **once** (never stored in plain text)
- DB stores `BCrypt(token)` — same approach as password hashing
- Token expires after **72 hours**
- Token is **single-use** (marked `USED` after acceptance)

---

### 6.6 Google OAuth2 Login

```
Browser                         API Server               Google OAuth2
   │                                │                          │
   │  GET /oauth2/authorize/google  │                          │
   │ ──────────────────────────────►│                          │
   │  302 Redirect ◄───────────────│                          │
   │                                │                          │
   │  GET accounts.google.com ─────────────────────────────► │
   │  [Google Login Page] ◄─────────────────────────────────  │
   │                                │                          │
   │  User grants permission ──────────────────────────────► │
   │                                │                          │
   │                                │  Callback with auth code │
   │                                │◄─────────────────────────│
   │                                │  Exchange for profile    │
   │                                │                          │
   │                                │  OAuth2LoginSuccessHandler:
   │                                │  - Find user by email    │
   │                                │  - If not found: create  │
   │                                │  - Generate JWT tokens   │
   │  Redirect with ?token=eyJ... ◄─│                          │
```

---

### 6.7 Password Reset Flow

```
User                            API Server                    PostgreSQL
  │                                 │                              │
  │  POST /api/v1/auth/forgot-password                            │
  │  { "email": "alex@barber.com" } │                              │
  │ ───────────────────────────────►│                              │
  │                                 │  Generate secure reset token │
  │                                 │  Hash token with BCrypt      │
  │                                 │  Store in password_reset_tokens ──►│
  │                                 │  Send email with reset link  │
  │  200 OK (always, even if email  │                              │
  │  not found — prevents enumeration attacks)                     │
  │◄───────────────────────────────│                              │
  │                                 │                              │
  │  POST /api/v1/auth/reset-password                             │
  │  { token, newPassword }         │                              │
  │ ───────────────────────────────►│                              │
  │                                 │  Validate token + expiry ───►│
  │                                 │  @StrongPassword check       │
  │                                 │  BCrypt hash new password    │
  │                                 │  UPDATE user password ───────────────►│
  │                                 │  DELETE reset token ─────────────────►│
  │                                 │  Revoke all refresh tokens ──────────►│
  │  200 OK                         │                              │
  │◄───────────────────────────────│                              │
```

---

## 7. Database Schema

### Entity Relationship Diagram

```
┌──────────────────────┐
│      businesses      │
├──────────────────────┤
│ id (UUID) PK         │
│ name (VARCHAR 100)   │
│ subdomain (UNIQUE)   │
│ created_at           │
│ updated_at           │
└──────────┬───────────┘
           │ 1
           │ N
┌──────────▼───────────┐      ┌──────────────────────┐
│        users         │      │   refresh_tokens     │
├──────────────────────┤      ├──────────────────────┤
│ id (UUID) PK         │ 1:N  │ id (UUID) PK         │
│ business_id (FK)     ├─────►│ user_id (FK)         │
│ first_name           │      │ token_hash           │
│ last_name            │      │ device_id            │
│ email (UNIQUE)       │      │ expires_at           │
│ password_hash        │      │ revoked              │
│ role (OWNER/STAFF)   │      │ created_at           │
│ oauth_provider       │      └──────────────────────┘
│ oauth_id             │
│ created_at           │      ┌──────────────────────┐
│ updated_at           │      │  invitation_tokens   │
└──────────┬───────────┘      ├──────────────────────┤
           │ 1                │ id (UUID) PK         │
           │ N                │ business_id (FK)     │
┌──────────▼───────────┐      │ invited_email        │
│      audit_logs      │      │ token_hash           │
├──────────────────────┤      │ expires_at           │
│ id (BIGSERIAL) PK    │      │ status (PENDING/USED)│
│ business_id (FK)     │      │ created_at           │
│ user_id (FK)         │      └──────────────────────┘
│ action (VARCHAR)     │
│ details (JSONB)      │      ┌──────────────────────┐
│ ip_address           │      │ password_reset_tokens│
│ user_agent           │      ├──────────────────────┤
│ created_at           │      │ id (UUID) PK         │
└──────────────────────┘      │ user_id (FK)         │
                              │ token_hash           │
                              │ expires_at           │
                              │ used                 │
                              │ created_at           │
                              └──────────────────────┘
```

### Flyway Migrations (Auto-run on startup)

```
V1 → Create businesses table
V2 → Create users table
V3 → Create refresh_tokens table (with device_id for multi-device support)
V4 → Create audit_logs table (JSONB details column)
V5 → Create invitation_tokens + password_reset_tokens tables
```

---

## 8. Rate Limiting & Brute Force Protection

The `RateLimitingFilter` uses **Redis atomic operations** for thread-safe rate limiting:

```
Algorithm: Sliding Window Counter
Key:       "rate:{client_ip}:{endpoint_path}"
TTL:       60 seconds (auto-expires from Redis)

Rules:
  Endpoint              │ Max Requests │ Window
  /api/v1/auth/login    │      5       │ 60 sec
  /api/v1/auth/register │      3       │ 60 sec

Flow:
  1. INCR rate:{ip}:{path}          ← Atomic Redis increment
  2. If result == 1: EXPIRE key 60  ← Set TTL on first request
  3. If count > limit: return 429   ← Block the request
  4. Else: continue chain
```

**Response when rate limited:**
```json
HTTP 429 Too Many Requests
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please try again later.",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

## 9. Audit Logging

Every sensitive action produces an immutable audit trail in the `audit_logs` table.

```json
{
  "id": 1,
  "businessId": "uuid-barber",
  "userId": "uuid-alex",
  "action": "USER_LOGIN",
  "details": {
    "email": "alex@barber.com",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0..."
  },
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Design decisions:**
- Written **asynchronously** — does not slow down API responses
- **No UPDATE or DELETE** allowed — append-only by design
- Stored as **JSONB** — flexible schema, fully queryable by PostgreSQL
- Includes `ip_address` and `user_agent` for forensic analysis

---

## 10. Structured Logging

Profile-aware logging powered by `logback-spring.xml`:

### Development (dev profile) — Human Readable
```
20:01:10.123 INFO  [main] com.bookly.BooklyApplication - Started BooklyApplication in 3.9s
20:01:10.456 DEBUG [http-8080-1] com.bookly.security.JwtAuthenticationFilter - Token validated for user: alex@barber.com
```

### Production (prod profile) — JSON (ingested by log aggregators)
```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "logger": "com.bookly.service.AuthService",
  "thread": "http-nio-8080-exec-1",
  "message": "User registered successfully",
  "businessId": "uuid-barber"
}
```

**Security rules for logs:**
- Passwords are **NEVER logged**
- JWT tokens are **NEVER logged**
- PII (emails, names) only logged at DEBUG level in dev profile

---

## 11. Health Checks & Observability

```
GET /actuator/health  →  { "status": "UP" }

Components checked:
  ✅ db        — PostgreSQL connection via HikariCP
  ✅ redis     — Redis PING command
  ✅ diskSpace — Available disk space
```

**Endpoint security:**

| Path | Access |
|---|---|
| `/actuator/health` | PUBLIC (for load balancer health checks) |
| `/actuator/info` | PUBLIC (app version info) |
| `/actuator/**` | SUPER_ADMIN only |

---

## 12. Containerization

### Running with Docker Compose (recommended for local/staging)

```bash
# Start everything (app + postgres + redis)
docker-compose up

# Start in background
docker-compose up -d

# View app logs
docker-compose logs -f app

# Stop everything
docker-compose down
```

### Services started

| Service | Port | Notes |
|---|---|---|
| `app` | 8080 | Spring Boot API (waits for DB + Redis healthcheck) |
| `postgres` | 5432 | PostgreSQL 16 with `bookly_db` database |
| `redis` | 6379 | Redis 7 |

### Dockerfile (Multi-Stage Build)

```
Stage 1 — Build:
  FROM eclipse-temurin:21-jdk-alpine
  ./mvnw package -DskipTests
  Produces: target/bookly.jar

Stage 2 — Runtime:
  FROM eclipse-temurin:21-jre-alpine
  Non-root user: bookly:bookly
  EXPOSE 8080
  CMD java -jar bookly.jar
```

Security practices applied:
- Non-root user in container (`USER bookly`)
- JRE-only runtime image (no JDK/compiler)
- Alpine Linux (minimal attack surface)

---

## 13. CI/CD Pipeline

GitHub Actions runs on every `push` and `pull_request` to `main`:

```yaml
Jobs:
  test:
    runs-on: ubuntu-latest
    services:
      postgres:   # Real PostgreSQL — no in-memory fakes
        image: postgres:16
      redis:      # Real Redis
        image: redis:7
    steps:
      1. Checkout code
      2. Set up Java 21
      3. Cache Maven dependencies (~/.m2)
      4. ./mvnw test -B
```

**Tests run in CI:**

| Test Class | What it tests |
|---|---|
| `BooklyApplicationTests` | Spring context loads, Flyway migrations, DB/Redis wiring |
| `AuthServiceTest` | Business logic: register, login, refresh, token invalidation |
| `AuthControllerTest` | HTTP layer: request validation, response codes, error formats |

---

## 14. API Documentation

### Interactive Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### OpenAPI JSON (for Postman / Insomnia import)
```
http://localhost:8080/v3/api-docs
```

### Key Endpoints Reference

| Method | Path | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | ❌ | Register a new business |
| `POST` | `/api/v1/auth/login` | ❌ | Login, receive JWT tokens |
| `POST` | `/api/v1/auth/refresh` | ❌ | Refresh access token |
| `POST` | `/api/v1/auth/forgot-password` | ❌ | Request password reset email |
| `POST` | `/api/v1/auth/reset-password` | ❌ | Complete password reset |
| `GET` | `/api/v1/auth/me` | ✅ Bearer | Get current user profile |
| `POST` | `/api/v1/invitations` | ✅ OWNER | Create employee invitation |
| `POST` | `/api/v1/invitations/accept` | ❌ | Accept invitation, create account |
| `GET` | `/oauth2/authorize/google` | ❌ | Start Google OAuth2 login flow |
| `GET` | `/actuator/health` | ❌ | Health check |

---

## 15. Configuration & Profiles

### Spring Profiles

| Profile | Activation | Use Case |
|---|---|---|
| `default` | No profile set | Local dev (relaxed checks) |
| `dev` | `-Dspring.profiles.active=dev` | Development with verbose logging |
| `prod` | `-Dspring.profiles.active=prod` | Production (strict, JSON logs) |

### Key Configuration

```yaml
# application.yml
app:
  jwt:
    secret: ${JWT_SECRET}         # Min 32 chars — fails fast in prod if weak
    expiration: 900000            # 15 minutes (ms)
    refresh-expiration: 604800000 # 7 days (ms)
  cors:
    allowed-origins:
      - http://localhost:3000     # Dev frontend
      - https://bookly.com        # Production

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bookly_db
  data:
    redis:
      host: localhost
      port: 6379
  flyway:
    enabled: true                 # Auto-migrate on startup
```

### Production Environment Variables

```bash
JWT_SECRET=<min-32-char-random-secret>
SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/bookly_db
SPRING_DATASOURCE_USERNAME=bookly_user
SPRING_DATASOURCE_PASSWORD=<db-password>
SPRING_DATA_REDIS_HOST=redis-host
SPRING_DATA_REDIS_PORT=6379
GOOGLE_CLIENT_ID=<from-google-cloud-console>
GOOGLE_CLIENT_SECRET=<from-google-cloud-console>
```

---

## 16. What's Next to Build

The auth, security, and multi-tenant foundation is complete. Here are the natural next phases:

### Phase 2 — Core Booking Engine

| Feature | Description |
|---|---|
| **Services** | Business defines bookable services (e.g., "Haircut - 30min - $25") |
| **Staff Schedules** | Working hours and breaks per employee |
| **Availability Engine** | Real-time slot calculation based on schedule + existing bookings |
| **Appointments** | Customer books a slot → creates appointment record |
| **Double-Booking Prevention** | Optimistic locking + unique constraints |

### Phase 3 — Customer Experience

| Feature | Description |
|---|---|
| **Public Booking Page** | `barber.bookly.com` → customer-facing booking UI |
| **Email Notifications** | Confirmation, 24h reminder, cancellation emails |
| **SMS Reminders** | Via Twilio or similar provider |
| **iCal Integration** | Add to Google Calendar / Apple Calendar |
| **Customer Accounts** | Repeat customers with full booking history |

### Phase 4 — Business Management

| Feature | Description |
|---|---|
| **Dashboard API** | Today's appointments, revenue, utilization metrics |
| **Stripe Payments** | Deposits + full payment at booking time |
| **Waitlist** | Auto-fill cancelled slots from a waitlist |
| **Reviews** | Post-appointment review collection |
| **SaaS Billing** | Monthly subscription billing per business tier |

---

*Bookly v1.0.0 | Spring Boot 3.3.1 | Java 21 | Last updated: June 2026*

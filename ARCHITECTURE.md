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
   - [Guest Booking & Tenant Resolution](#68-guest-booking--tenant-resolution)
   - [Cancellation Notice Policy Enforcement](#69-cancellation-notice-policy-enforcement)
   - [Idempotent Email Notifications](#610-idempotent-email-notifications)
   - [Reviews, Ratings & Customer Privacy Masking](#611-reviews-ratings--customer-privacy-masking)
7. [Database Schema](#7-database-schema)
8. [Rate Limiting & Brute Force Protection](#8-rate-limiting--brute-force-protection)
9. [Audit Logging](#9-audit-logging)
10. [Structured Logging](#10-structured-logging)
11. [Health Checks & Observability](#11-health-checks--observability)
12. [Containerization](#12-containerization)
13. [CI/CD Pipeline](#13-cicd-pipeline)
14. [API Documentation](#14-api-documentation)
15. [Configuration & Profiles](#15-configuration--profiles)
16. [Project Roadmap & Status](#16-project-roadmap--status)

---

## 1. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     Client Applications                      │
│     (Web Browser / Mobile App / Public Booking Page)         │
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
│   │  3. TenantInterceptor    ← Scopes tenant contexts   │   │
│   │  4. SecurityConfig       ← Role-based route guards  │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │                REST Controllers                      │   │
│   │   AuthController · InvitationController             │   │
│   │   ServiceController · StaffScheduleController       │   │
│   │   AvailabilityController · AppointmentController     │   │
│   │   PublicBookingController · CustomerController      │   │
│   │   BusinessSettingsController · ReviewController      │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │                  Service Layer                       │   │
│   │   AuthService · PublicBookingService · ReviewService │   │
│   │   CustomerService · AppointmentService               │   │
│   │   NotificationService (Async Email)                  │   │
│   └──────────────────────────┬──────────────────────────┘   │
│                              │                               │
│   ┌──────────────────────────▼──────────────────────────┐   │
│   │               Repository Layer (JPA)                 │   │
│   │   BusinessRepository · CustomerRepository            │   │
│   │   AppointmentRepository · ReviewRepository           │   │
│   │   NotificationLogRepository · UserRepository         │   │
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
   │  - customers    │               │    counters     │
   │  - appointments │               │  - availability │
   │  - reviews      │               │    cache slots  │
   │  - users/logs   │               └─────────────────┘
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
| Mappers | MapStruct | 1.5.x |
| Database | PostgreSQL | 16 |
| Cache / Rate Limiting | Redis | 7 |
| Mail | Jakarta Mail / Spring Mail (Brevo SMTP integration) | 3.x |
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
│   │   │   │   ├── RedisConfig.java            # RedisTemplate setup
│   │   │   │   └── AsyncConfig.java            # Async executors + scheduler
│   │   │   ├── controller/
│   │   │   │   ├── AuthController.java         # /api/v1/auth/*
│   │   │   │   ├── PublicBookingController.java# /api/v1/public/* (No JWT required)
│   │   │   │   ├── CustomerController.java      # /api/v1/customers/* (Bearer required)
│   │   │   │   ├── BusinessSettingsController.java # /api/v1/business/settings/*
│   │   │   │   └── ReviewController.java       # /api/v1/reviews/* (Moderation)
│   │   │   ├── dto/                            # Request/Response DTOs
│   │   │   ├── entity/                         # JPA Entities
│   │   │   │   ├── Business.java
│   │   │   │   ├── Customer.java               # Dedicated client profile
│   │   │   │   ├── Appointment.java            # Relies on Customer FK
│   │   │   │   ├── Review.java                 # Rating entity
│   │   │   │   ├── NotificationLog.java        # Email idempotency log
│   │   │   │   ├── User.java
│   │   │   │   ├── RefreshToken.java
│   │   │   │   └── AuditLog.java
│   │   │   ├── mapper/                         # MapStruct Mapper interfaces
│   │   │   │   ├── CustomerMapper.java
│   │   │   │   ├── AppointmentMapper.java
│   │   │   │   └── ReviewMapper.java           # Customer privacy masking rules
│   │   │   ├── repository/                     # Spring Data JPA repos
│   │   │   │   ├── CustomerRepository.java
│   │   │   │   ├── AppointmentRepository.java
│   │   │   │   ├── ReviewRepository.java
│   │   │   │   └── NotificationLogRepository.java
│   │   │   ├── security/                       # Filters, Tenant interception
│   │   │   ├── service/
│   │   │   │   ├── CustomerService.java
│   │   │   │   ├── PublicBookingService.java
│   │   │   │   ├── ReviewService.java
│   │   │   │   ├── NotificationService.java    # Async SMTP mail processor
│   │   │   │   └── ReminderSchedulerService.java# 24h cron reminder scheduler
│   │   └── resources/
│   │       ├── application.yml                 # Base configurations
│   │       └── db/migration/                  # Flyway SQL scripts (V1 to V13)
│   │           ├── V9__customers.sql
│   │           ├── V10__appointments_customer_fk_migration.sql
│   │           ├── V11__cancellation_policy.sql
│   │           ├── V12__notification_log.sql
│   │           └── V13__reviews.sql
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
   ├── If no token → skip (public endpoints like /api/v1/public/** allowed)
   ├── Validates JWT signature using secret key
   ├── Loads UserDetails from DB
   ├── Sets Authentication in SecurityContextHolder
   └── Continue

      │
      ▼
③ TenantInterceptor (Spring MVC HandlerInterceptor)
   ├── Sets TenantContext.setCurrentTenant(tenantId)
   ├── In authenticated flows: read businessId from authenticated user profile
   ├── In public flows: resolved dynamically by PublicBookingController via subdomain
   └── Continue

      │
      ▼
④ SecurityConfig (Spring Security Authorization)
   ├── Public routes: /auth/**, /public/**, /actuator/health → PERMIT ALL
   ├── /actuator/** → requires SUPER_ADMIN
   └── Protected routes → requires authentication & roles (BUSINESS_OWNER / EMPLOYEE)

      │
      ▼
⑤ Controller → Service → Repository
   ├── Business logic executes
   ├── JPA queries auto-filtered by tenant (Hibernate filter)
   └── Response serialized to JSON

      │
      ▼
⑥ AuditService & Async Triggers (async @EventListener / TaskExecutor)
   ├── Audit events captured in background
   └── Async SMTP notification mails sent (logged for idempotency)
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
All protected endpoints require a valid JWT Bearer token containing user details.

```
Token Structure:
  Header:  { "alg": "HS256", "typ": "JWT" }
  Payload: { "sub": "user@email.com",
              "businessId": "uuid",
              "role": "BUSINESS_OWNER",
              "iat": 1700000000,
              "exp": 1700000900 }   ← 15 min expiry
  Signature: HMAC-SHA256(header + payload, secretKey)
```

### Layer 3 — Multi-Tenant Isolation
Every DB query is automatically scoped to the current tenant.
`TenantContext` stores the `businessId` in a thread-local variable. Hibernate `@Filter` is applied to every entity:
`@Filter(name = "tenantFilter", condition = "business_id = :tenantId")`

### Layer 4 — Role-Based Access Control (RBAC)

| Role | Access |
|---|---|
| `SUPER_ADMIN` | Full access, including `/actuator/**` |
| `BUSINESS_OWNER` | Manage their own business, settings, staff schedule overrides, invitations, view reviews |
| `EMPLOYEE` | Access only their scheduled appointment data and read reviews |
| `PUBLIC/GUEST` | Browse catalog, select availability slots, book, cancel, and submit reviews |

### Layer 5 — Password Policy (@StrongPassword)
Enforced at registration & password reset:
```
Requirements:
  ✅ Minimum 8 characters
  ✅ At least 1 uppercase letter  (A-Z)
  ✅ At least 1 lowercase letter  (a-z)
  ✅ At least 1 digit             (0-9)
  ✅ At least 1 special character (!@#$%^&*...)
  ✅ Maximum 50 characters
```

### Layer 6 — Immutable Audit Logging
Every sensitive action is permanently recorded (no updates, no deletes).

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
```

---

### 6.3 Token Refresh

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

---

### 6.4 Multi-Tenant Isolation
Hibernate filters dynamically inject tenant constraints so that Business A's database scope is physically isolated from Business B.

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

---

### 6.6 Google OAuth2 Login
Allows user single sign-on mapping via Google authentication redirect handler.

---

### 6.7 Password Reset Flow
Initiates forgotten password link creation, invalidates current user refresh tokens upon completion.

---

### 6.8 Guest Booking & Tenant Resolution

Customers book appointments online without creating a password-secured account.

```
Client                      PublicBookingController          CustomerService       AppointmentService
  │                                    │                            │                     │
  │ POST /public/{subdomain}/bookings  │                            │                     │
  │ ──────────────────────────────────►│                            │                     │
  │                                    │  Lookup Business by        │                     │
  │                                    │  subdomain.                │                     │
  │                                    │  Set TenantContext         │                     │
  │                                    │  findOrCreateGuest() ─────►│                     │
  │                                    │  ◄──────────────────────── Customers Table       │
  │                                    │  createAppointment() ───────────────────────────►│
  │                                    │  ◄──────────────────────── Double-book check     │
  │                                    │                            │                     │
  │                                    │  Trigger Async Mail        │                     │
  │ 201 Created                        │  Notification              │                     │
  │◄───────────────────────────────────│                            │                     │
```

---

### 6.9 Cancellation Notice Policy Enforcement
*   Businesses configure minimum notice hours (e.g. `cancellationNoticeHours: 2`).
*   When a customer cancels via `POST /api/v1/public/{subdomain}/bookings/{id}/cancel`, the system retrieves the appointment start time and checks if the cancellation window is still open. If the current time is past the window, a `400 BadRequestException` is thrown.

---

### 6.10 Idempotent Email Notifications
To guarantee that email notifications (e.g., booking confirmations or reminders) are never sent twice:
```
Trigger Event ──► NotificationService ──► Check NotificationLog table 
                                                  │
                                                  ├──► Already SENT? ──► Skip sending
                                                  └──► NOT SENT? ──────► Send SMTP Mail ──► Save Log (SENT)
```
*   **Reminder Scheduler**: An hourly `@Scheduled` task queries appointments starting within the 23-25h window. If the scheduler runs again, the `uidx_notification_sent` index prevents double-sending.

---

### 6.11 Reviews, Ratings & Customer Privacy Masking
*   **Access Token**: The completed `bookingId` serves as the authorization token for public submissions.
*   **Completed check**: Reviews are limited to `COMPLETED` appointments within 14 days.
*   **Aggregations**: Average scores and review counts are calculated dynamically and populated in public service and staff catalogs.
*   **Privacy Masking**: Public reviews mask customer names (e.g. `Jane Doe` -> `Jane D.`) via MapStruct before JSON serialization.

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
│ cancellation_notice_ │
│   hours (INT)        │
└──────────┬───────────┘
           │ 1
           ├─────────────────────────┬─────────────────────────┐
           │ N                       │ N                       │ N
┌──────────▼───────────┐      ┌──────▼───────────────┐  ┌──────▼───────────────┐
│      customers       │      │        users         │  │       services       │
├──────────────────────┤      ├──────────────────────┤  ├──────────────────────┤
│ id (UUID) PK         │ 1:N  │ id (UUID) PK         │  │ id (UUID) PK         │
│ business_id (FK)     ├─────►│ business_id (FK)     │  │ business_id (FK)     │
│ first_name           │      │ role (Enum)          │  │ name                 │
│ email (UNIQUE/T)     │      └──────────┬───────────┘  │ price                │
└──────────┬───────────┘                 │ 1            └──────────┬───────────┘
           │ 1                           │ N                       │ 1
           │ N                           │                         │ N
┌──────────▼─────────────────────────────▼─────────────────────────▼───────────┐
│                                appointments                                  │
├──────────────────────────────────────────────────────────────────────────────┤
│ id (UUID) PK                                                                 │
│ business_id (FK)                                                             │
│ customer_id (FK to customers)                                                │
│ staff_id (FK to users)                                                       │
│ service_id (FK to services)                                                  │
│ status (PENDING / CONFIRMED / CANCELLED / COMPLETED)                          │
└──────────┬───────────────────────────────────────────────────────────────────┘
           │ 1
           ├─────────────────────────┐
           │ 1                       │ 1
┌──────────▼───────────┐      ┌──────▼───────────────┐
│       reviews        │      │   notification_log   │
├──────────────────────┤      ├──────────────────────┤
│ id (UUID) PK         │      │ id (UUID) PK         │
│ business_id (FK)     │      │ appointment_id (FK)  │
│ appointment_id (FK)  │      │ type (Enum)          │
│ rating (INT)         │      │ status (SENT/FAILED) │
│ comment (TEXT)       │      └──────────────────────┘
└──────────────────────┘
```

### Flyway Migrations (Auto-run on startup)

```
V1__init_schema.sql             → Creates businesses and users tables
V2__audit_logs.sql              → Creates audit_logs table (JSONB details)
V3__password_reset_tokens.sql   → Creates password_reset_tokens table
V4__multi_device_sessions.sql   → Modifies refresh_tokens table (device fingerprint support)
V5__invitation_tokens.sql       → Creates invitation_tokens table
V6__services.sql                → Creates bookable services table
V7__staff_schedules.sql         → Creates staff_schedules and overrides tables
V8__appointments.sql            → Creates appointment records table
V9__customers.sql               → Creates isolated customer profiles table
V10__appointments_customer_fk   → Migrates appointment FK from users to customers
V11__cancellation_policy.sql    → Adds cancellation_notice_hours to businesses
V12__notification_log.sql       → Creates idempotent notification logs table
V13__reviews.sql                → Creates reviews and ratings table
```

---

## 8. Rate Limiting & Brute Force Protection
The `RateLimitingFilter` uses **Redis atomic operations** for thread-safe sliding window rate limiting.

---

## 9. Audit Logging
Every sensitive action produces an immutable audit trail written asynchronously to the `audit_logs` table.

---

## 10. Structured Logging
Profile-aware logging powered by Logback outputs human-readable logs in development and structured JSON in production.

---

## 11. Health Checks & Observability
Spring Boot Actuator monitors system parameters, database connectivity, and Redis.

---

## 12. Containerization
Packaged with Docker and orchestrated using Docker Compose (app, postgres, and redis services).

---

## 13. CI/CD Pipeline
Builds and runs unit and integration tests against native service containers in GitHub Actions on every pull request.

---

## 14. API Documentation

### Interactive Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### Key Endpoints Reference

| Method | Path | Auth Required | Description |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | ❌ | Register a new business |
| `POST` | `/api/v1/auth/login` | ❌ | Login, receive JWT tokens |
| `POST` | `/api/v1/public/{subdomain}/services` | ❌ | List active services + average rating |
| `POST` | `/api/v1/public/{subdomain}/staff` | ❌ | List active staff members + average rating |
| `GET`  | `/api/v1/public/{subdomain}/availability`| ❌ | Fetch real-time available time slots |
| `POST` | `/api/v1/public/{subdomain}/bookings` | ❌ | Create a guest booking (sends email confirmation) |
| `POST` | `/api/v1/public/{subdomain}/bookings/{id}/cancel` | ❌ | Cancel booking (enforces business policy) |
| `POST` | `/api/v1/public/{subdomain}/bookings/{id}/reviews`| ❌ | Submit review rating (completed appointments) |
| `GET`  | `/api/v1/public/{subdomain}/services/{id}/reviews`| ❌ | View public service reviews (privacy-masked) |
| `GET`  | `/api/v1/public/{subdomain}/staff/{id}/reviews`| ❌ | View public staff reviews (privacy-masked) |
| `GET`  | `/api/v1/customers` | Bearer (Staff) | List business customer profiles |
| `GET`  | `/api/v1/reviews` | Bearer (Staff) | Dashboard review list for business owners |

---

## 15. Configuration & Profiles
Controlled using Spring profiles (`dev`/`prod`) and environment variables (`JWT_SECRET`, `MAIL_PASSWORD`).

---

## 16. Project Roadmap & Status

### Phase 1 — Multi-Tenancy & Auth (✅ Complete)
Built multi-tenant database partitioning schema, password resets, rate-limiting brute force protection, super admin tools, and user invitation tokens.

### Phase 2 — Core Booking Engine (✅ Complete)
Built bookable services catalog (`BookableService`), employee shifts and calendar schedules (`StaffSchedule`), availability engine (`AvailabilityService`), appointment records (`Appointment`), and optimistic double-booking prevention.

### Phase 3 — Customer Experience (✅ Complete)
Built public-facing guest booking flow (`PublicBookingService`), automatic customer profile creation/lookup (`Customer`), business-configurable cancellation window policy (`BusinessSettings/CancellationPolicy`), and async idempotent Brevo SMTP email notifications (`NotificationService`/`NotificationLog`).

### Phase 4 — Reviews & Ratings (✅ Complete)
Built post-appointment review system (`Review`) with 14-day completion windows, dynamic rating score aggregation, and privacy-focused name masking for public catalog listing.

---

## What's Next to Build
Natural extensions for subsequent phases:
*   **Stripe Payments**: Integrate online deposit or full payment capture at booking time.
*   **Twilio SMS Reminders**: Expand notifications from email-only to active SMS reminders.
*   **Waitlist**: Automatic waitlists that notify clients when slots open.
*   **iCal Calendar Sync Feed**: Export `.ics` feeds for Google/Apple Calendar.
*   **SaaS Tiered Subscription Plans**: Monthly subscription billing per business tier.
*   **Business Analytics Dashboard**: Graphing utilization rates, popular services, and employee productivity.

---

*Bookly v1.4.0 | Spring Boot 3.3.1 | Java 21 | Last updated: July 2026*

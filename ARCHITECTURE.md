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
16. [What's Next to Build](#16-whats-next-to-build)

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
│   │   AuthController · PublicBookingController           │   │
│   │   CustomerController · ReviewController              │   │
│   │   InvitationController · AppointmentController       │   │
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

### Layer 2 — JWT Authentication
All protected admin/staff endpoints require a valid JWT Bearer token containing user details.

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
Minimum 8 characters, uppercase, lowercase, digit, special character.

---

## 6. Core User Flows

### 6.1 Business Registration
Registers a business, matches the owner, sets default subdomain, and yields a JWT session token.

### 6.2 Login & JWT Authentication
Validates login request parameters, issues JWT access tokens and logs audits.

### 6.3 Token Refresh
Rotates tokens on every request to prevent replay attacks.

### 6.4 Multi-Tenant Isolation
Hibernate filters ensure databases are logically partitioned per business.

### 6.5 Employee Invitation Flow
Generates secure tokens and sends a signup email to onboard staff.

### 6.6 Google OAuth2 Login
Allows single sign-on mapping via Google authentication.

### 6.7 Password Reset Flow
Initiates forgotten password tokens, invalidates current refresh tokens on reset.

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

Businesses can configure a minimum cancellation notice period (e.g. `cancellationNoticeHours: 2`).

*   When a customer attempts to cancel their booking via `POST /api/v1/public/{subdomain}/bookings/{id}/cancel`, the system retrieves the appointment start time.
*   If the current time is past the cancellation window deadline (e.g. less than 2 hours before the appointment), a `400 BadRequestException` is thrown to enforce the policy.

---

### 6.10 Idempotent Email Notifications

To guarantee that email notifications (e.g., booking confirmations or reminders) are never sent twice:

```
Trigger Event ──► NotificationService ──► Check NotificationLog table 
                                                  │
                                                  ├──► Already SENT? ──► Skip sending
                                                  └──► NOT SENT? ──────► Send SMTP Mail ──► Save Log (SENT)
```
*   **Reminder Scheduler**: An hourly `@Scheduled` task queries appointments starting within the 23-25h window, sending out reminders. If the scheduler runs again, the `uidx_notification_sent` index prevents double-sending.

---

### 6.11 Reviews, Ratings & Customer Privacy Masking

After an appointment is completed, customers can leave a review rating (1-5) and an optional comment:
*   **Access Token**: The `bookingId` (appointment UUID) serves as the authorization token for public submissions.
*   **Completed check**: The appointment status must be `COMPLETED` and submitted within 14 days.
*   **Aggregations**: Ratings are aggregated to return the `averageRating` and `reviewCount` on services and staff endpoints.
*   **Privacy Masking**: Public reviews mask customer names (e.g. `Jane Doe` -> `Jane D.`) via [ReviewMapper.java](file:///Users/akhand/Desktop/Pro/bookly/src/main/java/com/bookly/mapper/ReviewMapper.java) before JSON serialization.

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
           ├─────────────────────────┐
           │ N                       │ N
┌──────────▼───────────┐      ┌──────▼───────────────┐
│      customers       │      │        users         │
├──────────────────────┤      ├──────────────────────┤
│ id (UUID) PK         │ 1:N  │ id (UUID) PK         │
│ business_id (FK)     ├─────►│ business_id (FK)     │
│ first_name           │      │ role (OWNER/EMPLOYEE)│
│ email (UNIQUE/T)     │      └──────────┬───────────┘
└──────────┬───────────┘                 │ 1
           │ 1                           │ N
           │ N                           │
┌──────────▼─────────────────────────────▼───────────┐
│                    appointments                    │
├────────────────────────────────────────────────────┤
│ id (UUID) PK                                       │
│ business_id (FK)                                   │
│ customer_id (FK to customers)                      │
│ staff_id (FK to users)                             │
│ service_id (FK to services)                        │
│ status (PENDING / CONFIRMED / CANCELLED / COMPLETED)│
└──────────┬─────────────────────────────────────────┘
           │ 1
           │ 1
┌──────────▼───────────┐      ┌──────────────────────┐
│       reviews        │      │   notification_log   │
├──────────────────────┤      ├──────────────────────┤
│ id (UUID) PK         │      │ id (UUID) PK         │
│ business_id (FK)     │      │ appointment_id (FK)  │
│ appointment_id (FK)  │      │ type (Enum)          │
│ rating (INT)         │      │ status (SENT/FAILED) │
│ comment (TEXT)       │      └──────────────────────┘
└──────────────────────┘
```

---

## 8. Rate Limiting & Brute Force Protection
Filters queries inside Redis using atomic counters to block brute-force traffic.

---

## 9. Audit Logging
Audit logs capture critical state transformations asynchronously inside the `audit_logs` table.

---

## 10. Structured Logging
Applies console formatters for local environments and structured JSON logs for log aggregators in production.

---

## 11. Health Checks & Observability
Spring Boot Actuator monitors database connectivity, disk usage, and local health.

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
Controlled using local Spring profiles (`dev`/`default`) and environment variables (`JWT_SECRET`, `MAIL_PASSWORD`) in cloud settings.

---

## 16. What's Next to Build

All core scheduling engines, guest flows, and reviews are complete. Future iterations can cover:
*   **Stripe Integration**: Require payments/deposits to secure slot bookings.
*   **Waitlist**: Automatic waitlists that notify clients when slots open.
*   **Calendar Synced Feeds**: Export `.ics` calendar sync feeds for staff.
*   **Subscription Plans**: Subscription billing tiers for business tenants.

---

*Bookly v1.4.0 | Spring Boot 3.3.1 | Java 21 | Last updated: July 2026*

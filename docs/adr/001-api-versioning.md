# ADR-001: API Versioning Strategy

## Status
**Accepted** — June 2026

## Context
Bookly is a multi-tenant SaaS platform that will need to evolve its API
over time without breaking existing client integrations (web, mobile, partner).

We evaluated three strategies:
1. **URL path versioning** — `/api/v1/...`, `/api/v2/...`
2. **Header versioning** — `Accept: application/vnd.bookly.v1+json`
3. **Query parameter** — `?version=1`

## Decision
**URL path versioning** (`/api/v1/...`) was chosen because:

- **Discoverable**: Versions are immediately visible in URLs, API docs, and logs.
- **Cacheable**: CDNs and proxies can cache different versions independently.
- **Ecosystem support**: Every HTTP client, API gateway, and load balancer
  understands path-based routing natively.
- **Low cognitive overhead**: New developers instantly know which version they
  are working with.

## Consequences
- Adding `/api/v2/` will require a new set of controllers or shared handler
  logic (not copy-paste duplication).
- We will maintain `/api/v1/` for a minimum of 12 months after `/api/v2/`
  launches, with deprecation headers (`Sunset`, `Deprecation`).
- No version negotiation complexity at the framework level.

## Convention
- All REST controllers use `@RequestMapping("/api/v1/...")`.
- Internal RPCs (if any) are **unversioned** under `/internal/`.

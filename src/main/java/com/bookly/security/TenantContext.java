package com.bookly.security;

import java.util.UUID;

/**
 * ThreadLocal holder for the current tenant's business ID.
 * Set by JwtAuthenticationFilter from the JWT businessId claim,
 * consumed by TenantInterceptor to enable Hibernate tenant filters.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(UUID businessId) {
        CURRENT_TENANT.set(businessId);
    }

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

package com.bookly.repository;

import com.bookly.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Customer} entities.
 * All queries are automatically scoped to the current tenant via the Hibernate
 * {@code tenantFilter} (enabled by {@link com.bookly.config.TenantInterceptor}).
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Finds a customer by their primary key, restricted to the given business.
     * Used for tenant-safe single-record lookups when the Hibernate filter is not active
     * (e.g. in async notification threads that run outside the MVC interceptor lifecycle).
     */
    Optional<Customer> findByIdAndBusiness_Id(UUID id, UUID businessId);

    /**
     * Looks up a customer by email within a specific business.
     * Used for guest booking's "find or create" logic.
     */
    Optional<Customer> findByEmailAndBusiness_Id(String email, UUID businessId);

    /** Checks whether a customer with the given email exists for a business. */
    boolean existsByEmailAndBusiness_Id(String email, UUID businessId);

    /** Paginated list of all active customers for a business (used by owner/staff views). */
    Page<Customer> findAllByBusiness_IdAndIsActiveTrue(UUID businessId, Pageable pageable);
}

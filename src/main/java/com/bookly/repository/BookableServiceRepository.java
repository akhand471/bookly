package com.bookly.repository;

import com.bookly.entity.BookableService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link BookableService}.
 * <p>
 * Belt-and-suspenders tenant isolation: queries explicitly filter by
 * {@code businessId} in addition to the Hibernate tenantFilter applied
 * by {@link com.bookly.config.TenantInterceptor}. This prevents accidental
 * cross-tenant data leakage if the filter is ever disabled in a test context.
 */
@Repository
public interface BookableServiceRepository extends JpaRepository<BookableService, UUID> {

    /**
     * List all active services for a tenant, paginated.
     * The Hibernate filter provides a second layer; the explicit businessId
     * parameter here is the primary guard.
     */
    Page<BookableService> findAllByBusiness_IdAndIsActive(UUID businessId, boolean isActive, Pageable pageable);

    /**
     * Non-paginated list of active services for public booking page.
     */
    List<BookableService> findAllByBusiness_IdAndIsActiveTrue(UUID businessId);

    /**
     * Fetch a single service only if it belongs to the given business.
     * Used for ownership verification on update/delete operations.
     */
    Optional<BookableService> findByIdAndBusiness_Id(UUID id, UUID businessId);

    /**
     * Case-insensitive duplicate-name check within a business (active services only).
     * The DB unique index on {@code lower(name)} is the ultimate guard; this
     * check provides a friendly error before hitting the constraint.
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM BookableService s
            WHERE s.business.id = :businessId
              AND LOWER(s.name) = LOWER(:name)
              AND s.isActive = true
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsActiveByNameAndBusiness(
            @Param("businessId") UUID businessId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);
}

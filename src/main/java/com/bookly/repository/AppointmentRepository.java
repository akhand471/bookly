package com.bookly.repository;

import com.bookly.entity.Appointment;
import com.bookly.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Appointment} with tenant-scoped queries.
 * Belt-and-suspenders: explicit {@code businessId} parameter on every query
 * in addition to the Hibernate tenantFilter.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    /**
     * Availability engine: all non-cancelled appointments for a staff member on a given day.
     * Single efficient query — no N+1.
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.staff.id = :staffId
              AND a.business.id = :businessId
              AND a.startTime >= :dayStart
              AND a.startTime < :dayEnd
              AND a.status != 'CANCELLED'
            ORDER BY a.startTime
            """)
    List<Appointment> findActiveByStaffAndDay(
            @Param("staffId") UUID staffId,
            @Param("businessId") UUID businessId,
            @Param("dayStart") OffsetDateTime dayStart,
            @Param("dayEnd") OffsetDateTime dayEnd);

    /**
     * Fetch a single appointment with ownership validation.
     */
    Optional<Appointment> findByIdAndBusiness_Id(UUID id, UUID businessId);

    /**
     * Paginated listing with optional filters.
     * All parameters except businessId are optional (null = no filter).
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.business.id = :businessId
              AND (:customerId IS NULL OR a.customer.id = :customerId)
              AND (:staffId IS NULL OR a.staff.id = :staffId)
              AND (CAST(:status AS string) IS NULL OR CAST(a.status AS string) = CAST(:status AS string))
              AND (:from IS NULL OR a.startTime >= :from)
              AND (:to IS NULL OR a.startTime <= :to)
            ORDER BY a.startTime DESC
            """)
    Page<Appointment> findFiltered(
            @Param("businessId") UUID businessId,
            @Param("customerId") UUID customerId,
            @Param("staffId") UUID staffId,
            @Param("status") AppointmentStatus status,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);

    /**
     * Conflict check used before inserting a new appointment (belt-and-suspenders
     * on top of the DB unique constraint). Checks for any active appointment for
     * a staff member that overlaps the proposed [start, end) window.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM Appointment a
            WHERE a.staff.id = :staffId
              AND a.status != 'CANCELLED'
              AND a.startTime < :end
              AND a.endTime > :start
            """)
    boolean existsOverlappingAppointment(
            @Param("staffId") UUID staffId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}

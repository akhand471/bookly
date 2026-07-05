package com.bookly.repository;

import com.bookly.entity.StaffSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffScheduleRepository extends JpaRepository<StaffSchedule, UUID> {

    /** Full weekly schedule for a staff member, eagerly fetching breaks to avoid N+1. */
    @Query("""
            SELECT s FROM StaffSchedule s
            LEFT JOIN FETCH s.breaks
            WHERE s.staff.id = :staffId
              AND s.business.id = :businessId
            ORDER BY s.dayOfWeek
            """)
    List<StaffSchedule> findByStaffAndBusiness(
            @Param("staffId") UUID staffId,
            @Param("businessId") UUID businessId);

    /** Single day lookup for the availability engine. */
    @Query("""
            SELECT s FROM StaffSchedule s
            LEFT JOIN FETCH s.breaks
            WHERE s.staff.id = :staffId
              AND s.business.id = :businessId
              AND s.dayOfWeek = :dayOfWeek
            """)
    Optional<StaffSchedule> findByStaffAndBusinessAndDay(
            @Param("staffId") UUID staffId,
            @Param("businessId") UUID businessId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek);

    /** Delete all schedule rows for a staff member before replacing them. */
    void deleteAllByStaff_IdAndBusiness_Id(UUID staffId, UUID businessId);
}

package com.bookly.repository;

import com.bookly.entity.StaffScheduleOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffScheduleOverrideRepository extends JpaRepository<StaffScheduleOverride, UUID> {

    /** All overrides for a staff member within a date range (used by availability engine). */
    List<StaffScheduleOverride> findAllByStaff_IdAndBusiness_IdAndOverrideDateBetween(
            UUID staffId, UUID businessId, LocalDate from, LocalDate to);

    /** Single date lookup (unique per staff_id + override_date). */
    Optional<StaffScheduleOverride> findByStaff_IdAndBusiness_IdAndOverrideDate(
            UUID staffId, UUID businessId, LocalDate date);

    /** Delete a specific override by date. */
    void deleteByStaff_IdAndBusiness_IdAndOverrideDate(UUID staffId, UUID businessId, LocalDate date);
}

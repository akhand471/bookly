package com.bookly.repository;

import com.bookly.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<Review, UUID> {

    Optional<Review> findByAppointment_Id(UUID appointmentId);

    Page<Review> findAllByBusiness_Id(UUID businessId, Pageable pageable);

    Page<Review> findAllByService_IdAndBusiness_Id(UUID serviceId, UUID businessId, Pageable pageable);

    Page<Review> findAllByStaff_IdAndBusiness_Id(UUID staffId, UUID businessId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.service.id = :serviceId")
    double getAverageRatingForService(@Param("serviceId") UUID serviceId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.service.id = :serviceId")
    long getReviewCountForService(@Param("serviceId") UUID serviceId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.staff.id = :staffId")
    double getAverageRatingForStaff(@Param("staffId") UUID staffId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.staff.id = :staffId")
    long getReviewCountForStaff(@Param("staffId") UUID staffId);
}

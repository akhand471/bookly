package com.bookly.repository;

import com.bookly.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /** Returns all enabled users (staff + owners) for a business. Used by public staff listing. */
    List<User> findAllByBusiness_IdAndIsEnabledTrue(UUID businessId);
}


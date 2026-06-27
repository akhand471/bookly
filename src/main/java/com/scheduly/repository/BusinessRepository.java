package com.scheduly.repository;

import com.scheduly.entity.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BusinessRepository extends JpaRepository<Business, UUID> {
    Optional<Business> findBySubdomain(String subdomain);
    boolean existsBySubdomain(String subdomain);
}

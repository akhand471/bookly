package com.bookly.repository;

import com.bookly.entity.RefreshToken;
import com.bookly.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUserAndDeviceFingerprint(User user, String deviceFingerprint);

    @Modifying
    @Transactional  // Required: @Modifying operations must execute inside a transaction
    int deleteByUser(User user);

    @Modifying
    @Transactional
    int deleteByUserAndDeviceFingerprint(User user, String deviceFingerprint);
}

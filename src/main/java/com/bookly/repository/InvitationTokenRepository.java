package com.bookly.repository;

import com.bookly.entity.InvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvitationTokenRepository extends JpaRepository<InvitationToken, UUID> {
    Optional<InvitationToken> findByTokenHash(String tokenHash);
}

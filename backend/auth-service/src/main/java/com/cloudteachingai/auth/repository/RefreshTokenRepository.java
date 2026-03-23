package com.cloudteachingai.auth.repository;

import com.cloudteachingai.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenIdAndRevokedFalse(String tokenId);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}

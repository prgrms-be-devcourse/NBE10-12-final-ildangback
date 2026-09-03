package com.gommit.domain.user.repository;

import com.gommit.domain.user.entity.EmailToken;
import com.gommit.domain.user.entity.EmailTokenType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHash(String tokenHash);

    Optional<EmailToken> findFirstByUserIdAndTokenTypeOrderByIdDesc(Long userId, EmailTokenType tokenType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int useIfUnused(@Param("id") Long id, @Param("now") LocalDateTime now);
}

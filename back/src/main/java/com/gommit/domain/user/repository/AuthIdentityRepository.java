package com.gommit.domain.user.repository;

import com.gommit.domain.user.entity.AuthIdentity;
import com.gommit.domain.user.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, Long> {

    Optional<AuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);

    void deleteByUserId(Long userId);
}

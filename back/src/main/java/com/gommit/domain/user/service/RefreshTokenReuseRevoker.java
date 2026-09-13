package com.gommit.domain.user.service;

import com.gommit.domain.user.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefreshTokenReuseRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final TransactionTemplate transactionTemplate;

    public RefreshTokenReuseRevoker(
            RefreshTokenRepository refreshTokenRepository, PlatformTransactionManager transactionManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // 사용자의 모든 RT 폐기
    public void revokeAll(Long userId, LocalDateTime now) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                transactionTemplate.executeWithoutResult(
                        ignored -> refreshTokenRepository.revokeAllByUserId(userId, now));
            }
        });
    }
}

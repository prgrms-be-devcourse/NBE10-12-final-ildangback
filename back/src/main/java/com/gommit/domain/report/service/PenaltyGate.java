package com.gommit.domain.report.service;

import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.PenaltyType;
import com.gommit.domain.report.repository.PenaltyRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PenaltyGate {

    private final PenaltyRepository penaltyRepository;

    // 유효한 정지가 있으면 토큰 발급을 거절한다
    @Transactional(readOnly = true)
    public void verifyNotBlocked(Long userId) {
        findBlocking(userId).ifPresent(penalty -> {
            throw new BusinessException(
                    penalty.getPenaltyType() == PenaltyType.PERMANENT_BAN
                            ? ErrorCode.ACCOUNT_BANNED
                            : ErrorCode.ACCOUNT_SUSPENDED);
        });
    }

    // 로그인을 막고 있는 제재
    private Optional<Penalty> findBlocking(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return penaltyRepository.findAllByUserIdAndRevokedAtIsNull(userId).stream()
                .filter(penalty -> penalty.blocksLogin(now))
                .findFirst();
    }
}

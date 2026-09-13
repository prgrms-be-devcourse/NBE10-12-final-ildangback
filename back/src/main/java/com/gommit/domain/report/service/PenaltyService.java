package com.gommit.domain.report.service;

import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.report.dto.request.PenaltyCommand;
import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.PenaltyType;
import com.gommit.domain.report.repository.PenaltyCount;
import com.gommit.domain.report.repository.PenaltyRepository;
import com.gommit.domain.user.service.RefreshTokenService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PenaltyService {

    private static final String FORFEIT_SOURCE = "제재 포인트 압수";
    private static final String REFUND_SOURCE = "이의제기 인용 환급";

    private final PenaltyRepository penaltyRepository;
    private final PersonalPointService personalPointService;
    private final RefreshTokenService refreshTokenService;
    private final GroupService groupService;

    // 판정 결과로 제재를 건다
    @Transactional
    public List<Penalty> apply(Long reportId, Long userId, List<PenaltyCommand> commands) {
        if (commands.isEmpty()) {
            throw new BusinessException(ErrorCode.PENALTY_REQUIRED);
        }
        List<Penalty> penalties = penaltyRepository.saveAll(commands.stream()
                .map(command -> create(reportId, userId, command))
                .toList());

        penalties.stream()
                .filter(penalty -> penalty.getPenaltyType() == PenaltyType.POINT_FORFEIT)
                .forEach(penalty -> forfeit(userId, penalty));

        LocalDateTime now = LocalDateTime.now();
        if (penalties.stream().anyMatch(penalty -> penalty.blocksLogin(now))) {
            refreshTokenService.revokeAll(userId);
        }
        if (penalties.stream().anyMatch(penalty -> penalty.getPenaltyType() == PenaltyType.PERMANENT_BAN)) {
            groupService.leaveAllGroupsOnAccountDeletion(userId);
        }
        return penalties;
    }

    // 이의제기 인용 시 그 신고에 달린 제재를 전부 해제한다
    @Transactional
    public List<Penalty> revokeAllByReport(Long reportId) {
        List<Penalty> penalties = penaltyRepository.findAllByReportId(reportId);
        penalties.stream().filter(penalty -> !penalty.isRevoked()).forEach(penalty -> {
            penalty.revoke();
            if (penalty.getPenaltyType() == PenaltyType.POINT_FORFEIT) {
                refund(penalty);
            }
        });
        return penalties;
    }

    public List<Penalty> findByReport(Long reportId) {
        return penaltyRepository.findAllByReportId(reportId);
    }

    // 해제되지 않은 제재 건수
    public Map<Long, Long> countActiveByUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (PenaltyCount row : penaltyRepository.countByUserIds(userIds)) {
            counts.put(row.getUserId(), row.getCount());
        }
        return counts;
    }

    // 관리자 제재 종류 선택 후 생성
    private Penalty create(Long reportId, Long userId, PenaltyCommand command) {
        return switch (command.penaltyType()) {
            case WARNING -> Penalty.warning(reportId, userId);
            case SUSPENSION -> Penalty.suspension(reportId, userId, requireDays(command));
            case PERMANENT_BAN -> Penalty.permanentBan(reportId, userId);
            case POINT_FORFEIT -> Penalty.pointForfeit(reportId, userId, requireAmount(command));
        };
    }

    // 포인트 압수. 잔액보다 크면 잔액까지만 깎는다 — 음수 잔액을 만들면 포인트 도메인 전체가 음수를 고려해야 한다.
    private void forfeit(Long userId, Penalty penalty) {
        int balance = personalPointService.getMyBalance(userId).balance();
        int actual = Math.min(penalty.getAmount(), balance);
        penalty.recordForfeited(actual);
        if (actual > 0) {
            personalPointService.deduct(userId, actual, UserPointReason.PENALTY_FORFEIT, FORFEIT_SOURCE);
        }
    }

    // 압수한 포인트를 다시 돌려준다
    private void refund(Penalty penalty) {
        if (penalty.getAmount() > 0) {
            personalPointService.refund(
                    penalty.getUserId(), penalty.getAmount(), UserPointReason.PENALTY_REFUND, REFUND_SOURCE);
        }
    }

    // 정지 일수 확인
    private int requireDays(PenaltyCommand command) {
        Integer days = command.suspensionDays();
        if (days == null || days < Penalty.MIN_SUSPENSION_DAYS || days > Penalty.MAX_SUSPENSION_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_SUSPENSION_DAYS);
        }
        return days;
    }

    // 압수 포인트 확인
    private int requireAmount(PenaltyCommand command) {
        Integer amount = command.amount();
        if (amount == null || amount < 1) {
            throw new BusinessException(ErrorCode.INVALID_FORFEIT_AMOUNT);
        }
        return amount;
    }
}

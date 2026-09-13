package com.gommit.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.report.dto.request.PenaltyCommand;
import com.gommit.domain.report.entity.Penalty;
import com.gommit.domain.report.entity.PenaltyType;
import com.gommit.domain.report.repository.PenaltyRepository;
import com.gommit.domain.user.service.RefreshTokenService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("PenaltyService")
class PenaltyServiceTest {

    private static final Long REPORT_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private PenaltyRepository penaltyRepository;

    @Mock
    private PersonalPointService personalPointService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private GroupService groupService;

    @InjectMocks
    private PenaltyService penaltyService;

    @Test
    @DisplayName("제재를 하나도 고르지 않으면 거절한다")
    void rejectsEmptyPenalties() {
        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PENALTY_REQUIRED);
    }

    @Test
    @DisplayName("기간 정지에 일수가 없으면 거절한다")
    void rejectsSuspensionWithoutDays() {
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.SUSPENSION, null, null));

        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SUSPENSION_DAYS);
    }

    @Test
    @DisplayName("기간 정지 일수가 365를 넘으면 거절한다")
    void rejectsSuspensionOverMaxDays() {
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.SUSPENSION, 366, null));

        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SUSPENSION_DAYS);
    }

    @Test
    @DisplayName("기간 정지 일수가 1 미만이면 거절한다")
    void rejectsSuspensionUnderMinDays() {
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.SUSPENSION, 0, null));

        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SUSPENSION_DAYS);
    }

    @Test
    @DisplayName("포인트 압수에 금액이 없으면 거절한다")
    void rejectsForfeitWithoutAmount() {
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.POINT_FORFEIT, null, null));

        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FORFEIT_AMOUNT);
    }

    @Test
    @DisplayName("포인트 압수 금액이 1 미만이면 거절한다")
    void rejectsForfeitUnderMinAmount() {
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.POINT_FORFEIT, null, 0));

        assertThatThrownBy(() -> penaltyService.apply(REPORT_ID, USER_ID, commands))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_FORFEIT_AMOUNT);
    }

    @Test
    @DisplayName("대상이 없으면 제재 건수를 조회하지 않는다")
    void countsNothingForEmptyUserIds() {
        assertThat(penaltyService.countActiveByUsers(List.of())).isEmpty();
    }

    @Test
    @DisplayName("정지와 압수를 함께 걸면 종료 시각과 금액이 각각 채워진다")
    void createsSuspensionAndForfeitTogether() {
        given(penaltyRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(personalPointService.getMyBalance(USER_ID)).willReturn(new PointBalanceResponse(500, 0, 0, 0));
        List<PenaltyCommand> commands = List.of(
                new PenaltyCommand(PenaltyType.SUSPENSION, 7, null),
                new PenaltyCommand(PenaltyType.POINT_FORFEIT, null, 100));

        List<Penalty> penalties = penaltyService.apply(REPORT_ID, USER_ID, commands);

        assertThat(penalties).hasSize(2);
        assertThat(penalties.get(0).getEndsAt()).isNotNull();
        assertThat(penalties.get(0).getAmount()).isNull();
        assertThat(penalties.get(1).getEndsAt()).isNull();
        assertThat(penalties.get(1).getAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("영구 정지는 종료 시각을 비운다")
    void permanentBanHasNoEndsAt() {
        given(penaltyRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.PERMANENT_BAN, null, null));

        List<Penalty> penalties = penaltyService.apply(REPORT_ID, USER_ID, commands);

        assertThat(penalties.get(0).getEndsAt()).isNull();
        assertThat(penalties.get(0).blocksLogin(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("잔액보다 큰 압수는 잔액까지만 깎고 그 값을 기록한다")
    void forfeitCapsAtBalance() {
        given(penaltyRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
        given(personalPointService.getMyBalance(USER_ID)).willReturn(new PointBalanceResponse(50, 0, 0, 0));
        List<PenaltyCommand> commands = List.of(new PenaltyCommand(PenaltyType.POINT_FORFEIT, null, 200));

        List<Penalty> penalties = penaltyService.apply(REPORT_ID, USER_ID, commands);

        assertThat(penalties.get(0).getAmount()).isEqualTo(50);
    }

    @Test
    @DisplayName("인용하면 해제되지 않은 제재만 해제 대상이 된다")
    void revokesOnlyActivePenalties() {
        Penalty alreadyRevoked = Penalty.suspension(REPORT_ID, USER_ID, 3);
        alreadyRevoked.revoke();
        LocalDateTime revokedAt = alreadyRevoked.getRevokedAt();
        Penalty active = Penalty.pointForfeit(REPORT_ID, USER_ID, 100);
        given(penaltyRepository.findAllByReportId(anyLong())).willReturn(List.of(alreadyRevoked, active));

        penaltyService.revokeAllByReport(REPORT_ID);

        assertThat(active.getRevokedAt()).isNotNull();
        assertThat(alreadyRevoked.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    @DisplayName("인용하면 실제로 깎인 금액만큼 환급한다")
    void revokeRefundsForfeitedAmount() {
        Penalty forfeit = Penalty.pointForfeit(REPORT_ID, USER_ID, 200);
        forfeit.recordForfeited(50);
        given(penaltyRepository.findAllByReportId(anyLong())).willReturn(List.of(forfeit));

        penaltyService.revokeAllByReport(REPORT_ID);

        verify(personalPointService).refund(eq(USER_ID), eq(50), eq(UserPointReason.PENALTY_REFUND), anyString());
    }

    @Test
    @DisplayName("한 푼도 못 깎았으면 환급하지 않는다")
    void revokeSkipsRefundWhenNothingWasTaken() {
        Penalty forfeit = Penalty.pointForfeit(REPORT_ID, USER_ID, 200);
        forfeit.recordForfeited(0);
        given(penaltyRepository.findAllByReportId(anyLong())).willReturn(List.of(forfeit));

        penaltyService.revokeAllByReport(REPORT_ID);

        verify(personalPointService, never()).refund(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("정지 해제는 환급을 부르지 않는다")
    void revokeDoesNotRefundForSuspension() {
        given(penaltyRepository.findAllByReportId(anyLong()))
                .willReturn(List.of(Penalty.suspension(REPORT_ID, USER_ID, 3)));

        penaltyService.revokeAllByReport(REPORT_ID);

        verify(personalPointService, never()).refund(anyLong(), anyInt(), any(), anyString());
    }
}

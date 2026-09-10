package com.gommit.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.point.dto.request.PeriodFilter;
import com.gommit.domain.point.dto.request.PointChangeType;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.entity.UserPoint;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PersonalPointServiceTest {

    @Mock
    private UserPointHistoryRepository userPointHistoryRepository;

    @Mock
    private UserPointRepository userPointRepository;

    private final PointPeriodCalculator periodCalculator = new PointPeriodCalculator(new BusinessClock(
            Clock.fixed(LocalDateTime.of(2026, 9, 15, 14, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC"))));

    private PersonalPointService personalPointService;

    @BeforeEach
    void setUp() {
        personalPointService =
                new PersonalPointService(userPointHistoryRepository, userPointRepository, periodCalculator);
    }

    private UserPoint userPoint(int balance) {
        UserPoint point = UserPoint.init(1L);
        point.add(balance);
        return point;
    }

    private UserPointHistory userHistory(Long id, Long userId, int amount, int balanceAfter) {
        UserPointHistory history =
                UserPointHistory.of(userId, null, "오운완", amount, UserPointReason.CHECK_IN, balanceAfter);
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    @Nested
    @DisplayName("reward - 개인 포인트 지급")
    class Reward {

        @Test
        @DisplayName("잔액 행이 없으면 0에서 시작해서 지급된 만큼 잔액이 쌓인다")
        void rewardsFromZeroWhenNoBalanceRow() {
            when(userPointRepository.existsByUserId(1L)).thenReturn(false);
            when(userPointRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(UserPoint.init(1L)));

            personalPointService.reward(1L, 32L, 40, UserPointReason.CHECK_IN, "오운완");

            verify(userPointRepository).insertZeroBalanceIfAbsent(eq(1L), any(LocalDateTime.class));
            ArgumentCaptor<UserPointHistory> captor = ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(40);
            assertThat(captor.getValue().getAmount()).isEqualTo(40);
        }

        @Test
        @DisplayName("기존 잔액이 있으면 그 위에 더해서 지급된다")
        void rewardsOnTopOfExistingBalance() {
            when(userPointRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(userPoint(1000)));

            personalPointService.reward(1L, 32L, 40, UserPointReason.CHECK_IN, "오운완");

            ArgumentCaptor<UserPointHistory> captor = ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(1040);
        }

        @Test
        @DisplayName("amount가 0 이하면 INVALID_INPUT_VALUE 예외가 발생하고 저장하지 않는다")
        void throwsWhenAmountIsNotPositive() {
            assertThatThrownBy(() -> personalPointService.reward(1L, 32L, 0, UserPointReason.CHECK_IN, "오운완"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userPointHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deduct - 개인 포인트 차감")
    class Deduct {

        @Test
        @DisplayName("잔액이 충분하면 차감되고 amount는 음수로 저장된다")
        void deductsWhenBalanceIsSufficient() {
            when(userPointRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(userPoint(1000)));

            personalPointService.deduct(1L, 300, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

            ArgumentCaptor<UserPointHistory> captor = ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualTo(-300);
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(700);
        }

        @Test
        @DisplayName("잔액이 부족하면 POINT_INSUFFICIENT 예외가 발생하고 저장하지 않는다")
        void throwsWhenBalanceIsInsufficient() {
            when(userPointRepository.findWithLockByUserId(1L)).thenReturn(Optional.of(userPoint(100)));

            assertThatThrownBy(() -> personalPointService.deduct(1L, 300, UserPointReason.ITEM_PURCHASE, "핑크 왕리본"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

            verify(userPointHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("amount가 0 이하면 INVALID_INPUT_VALUE 예외가 발생하고 저장하지 않는다")
        void throwsWhenAmountIsNotPositive() {
            assertThatThrownBy(() -> personalPointService.deduct(1L, -10, UserPointReason.ITEM_PURCHASE, "핑크 왕리본"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userPointHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getMyBalance - 개인 포인트 잔액 조회")
    class GetMyBalance {

        @Test
        @DisplayName("현재잔액·이번달적립·이번달차감·누적적립을 함께 반환한다")
        void returnsBalanceSummary() {
            when(userPointRepository.findByUserId(1L)).thenReturn(Optional.of(userPoint(1240)));
            when(userPointHistoryRepository.sumEarnedFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(520);
            when(userPointHistoryRepository.sumSpentFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(300);
            when(userPointHistoryRepository.sumEarnedAll(1L)).thenReturn(4860);

            PointBalanceResponse response = personalPointService.getMyBalance(1L);

            assertThat(response.balance()).isEqualTo(1240);
            assertThat(response.monthlyEarned()).isEqualTo(520);
            assertThat(response.monthlySpent()).isEqualTo(300);
            assertThat(response.totalEarned()).isEqualTo(4860);
        }

        @Test
        @DisplayName("잔액 행이 아직 없으면 0으로 반환한다")
        void returnsZeroWhenNoBalanceRow() {
            when(userPointRepository.findByUserId(eq(1L))).thenReturn(Optional.empty());
            when(userPointHistoryRepository.sumEarnedFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(userPointHistoryRepository.sumSpentFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(userPointHistoryRepository.sumEarnedAll(1L)).thenReturn(0);

            PointBalanceResponse response = personalPointService.getMyBalance(1L);

            assertThat(response.balance()).isZero();
        }
    }

    @Nested
    @DisplayName("getMyHistories - 개인 포인트 이력 커서 조회")
    class GetMyHistories {

        @Test
        @DisplayName("size보다 한 건 더 조회되면 hasNext=true, nextCursor는 잘린 마지막 항목 id다")
        void returnsHasNextTrueWhenMoreRowsExist() {
            List<UserPointHistory> rows =
                    List.of(userHistory(3L, 1L, 40, 300), userHistory(2L, 1L, 40, 260), userHistory(1L, 1L, 40, 220));
            when(userPointHistoryRepository.findHistories(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(rows);

            SliceResponse<?> result = personalPointService.getMyHistories(
                    1L, PeriodFilter.ALL, PointChangeType.ALL, null, null, null, null, 2);

            assertThat(result.content()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(2L);
        }

        @Test
        @DisplayName("size만큼만 조회되면 hasNext=false, nextCursor=null이다")
        void returnsHasNextFalseWhenNoMoreRows() {
            List<UserPointHistory> rows = List.of(userHistory(1L, 1L, 40, 300));
            when(userPointHistoryRepository.findHistories(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(rows);

            SliceResponse<?> result = personalPointService.getMyHistories(
                    1L, PeriodFilter.ALL, PointChangeType.ALL, null, null, null, null, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("THIS_MONTH면 이번 달 시작(영업일 04:00 기준) 이후만 조회하고 종료는 제한하지 않는다")
        void filtersFromStartOfThisBusinessMonth() {
            when(userPointHistoryRepository.findHistories(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            personalPointService.getMyHistories(
                    1L, PeriodFilter.THIS_MONTH, PointChangeType.ALL, null, null, null, null, 20);

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userPointHistoryRepository)
                    .findHistories(any(), any(), from.capture(), to.capture(), any(), any(), any());
            // periodCalculator 는 2026-09-15 로 고정된 BusinessClock 사용 → 이번 영업월 1일 = 2026-09-01
            assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
            assertThat(to.getValue()).isNull();
        }

        @Test
        @DisplayName("LAST_MONTH면 지난 달 영업일 04:00부터 이번 달 영업일 04:00 전까지만 조회한다")
        void filtersLastBusinessMonthRange() {
            when(userPointHistoryRepository.findHistories(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            personalPointService.getMyHistories(
                    1L, PeriodFilter.LAST_MONTH, PointChangeType.ALL, null, null, null, null, 20);

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userPointHistoryRepository)
                    .findHistories(any(), any(), from.capture(), to.capture(), any(), any(), any());
            // 고정 BusinessClock(2026-09-15) → 이번 영업월 1일 = 2026-09-01, 지난달 = 2026-08-01
            assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 1, 4, 0));
            assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
        }

        @Test
        @DisplayName("from/to(직접설정)가 있으면 period는 무시하고 그 범위를 쓴다")
        void customRangeOverridesPeriod() {
            when(userPointHistoryRepository.findHistories(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            personalPointService.getMyHistories(
                    1L,
                    PeriodFilter.THIS_MONTH,
                    PointChangeType.ALL,
                    null,
                    LocalDate.of(2026, 8, 1),
                    LocalDate.of(2026, 8, 31),
                    null,
                    20);

            ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(userPointHistoryRepository)
                    .findHistories(any(), any(), from.capture(), to.capture(), any(), any(), any());
            assertThat(from.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 1, 4, 0));
            assertThat(to.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
        }

        @Test
        @DisplayName("from만 있으면 INVALID_INPUT_VALUE 예외가 발생한다")
        void customRangeFromOnly() {
            assertThatThrownBy(() -> personalPointService.getMyHistories(
                            1L, null, PointChangeType.ALL, null, LocalDate.of(2026, 8, 1), null, null, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userPointHistoryRepository, never()).findHistories(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("to만 있으면 INVALID_INPUT_VALUE 예외가 발생한다")
        void customRangeToOnly() {
            assertThatThrownBy(() -> personalPointService.getMyHistories(
                            1L, null, PointChangeType.ALL, null, null, LocalDate.of(2026, 8, 31), null, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userPointHistoryRepository, never()).findHistories(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("from이 to보다 미래면 INVALID_INPUT_VALUE 예외가 발생한다")
        void throwsWhenFromIsAfterTo() {
            assertThatThrownBy(() -> personalPointService.getMyHistories(
                            1L,
                            null,
                            PointChangeType.ALL,
                            null,
                            LocalDate.of(2026, 8, 31),
                            LocalDate.of(2026, 8, 1),
                            null,
                            20))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(userPointHistoryRepository, never()).findHistories(any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getMyHistoryDetail - 개인 포인트 이력 상세")
    class GetMyHistoryDetail {

        @Test
        @DisplayName("존재하지 않으면 POINT_HISTORY_NOT_FOUND")
        void throwsWhenNotFound() {
            when(userPointHistoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> personalPointService.getMyHistoryDetail(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 이력이면 존재해도 POINT_HISTORY_NOT_FOUND(본인 것만 조회 가능)")
        void throwsWhenOwnedByAnotherUser() {
            when(userPointHistoryRepository.findById(105L)).thenReturn(Optional.of(userHistory(105L, 2L, 40, 1240)));

            assertThatThrownBy(() -> personalPointService.getMyHistoryDetail(1L, 105L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }
    }
}

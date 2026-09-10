package com.gommit.domain.point.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.point.entity.GroupPoint;
import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.repository.GroupPointHistoryRepository;
import com.gommit.domain.point.repository.GroupPointRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
class GroupPointServiceTest {

    @Mock
    private GroupPointHistoryRepository groupPointHistoryRepository;

    @Mock
    private GroupPointRepository groupPointRepository;

    private GroupPointService groupPointService;

    @BeforeEach
    void setUp() {
        groupPointService = new GroupPointService(
                groupPointHistoryRepository,
                groupPointRepository,
                new PointPeriodCalculator(new BusinessClock(Clock.fixed(
                        LocalDateTime.of(2026, 9, 15, 14, 0).toInstant(ZoneOffset.UTC), ZoneId.of("UTC")))));
    }

    private GroupPoint groupPoint(int balance) {
        GroupPoint point = GroupPoint.init(12L);
        point.add(balance);
        return point;
    }

    private GroupPointHistory groupHistory(Long id, Long groupId, int amount, int balanceAfter) {
        GroupPointHistory history =
                GroupPointHistory.of(groupId, "오운완", amount, GroupPointReason.DAILY_ALL_COMPLETE, balanceAfter);
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    @Nested
    @DisplayName("reward / deduct - 그룹 포인트")
    class RewardAndDeduct {

        @Test
        @DisplayName("그룹 포인트도 기존 잔액 위에 지급된다")
        void rewardsGroup() {
            when(groupPointRepository.findWithLockByGroupId(12L)).thenReturn(Optional.of(groupPoint(8420)));

            groupPointService.reward(12L, 30, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");

            ArgumentCaptor<GroupPointHistory> captor = ArgumentCaptor.forClass(GroupPointHistory.class);
            verify(groupPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(8450);
        }

        @Test
        @DisplayName("그룹 잔액 행이 없으면 0에서 시작해서 지급된 만큼 잔액이 쌓인다")
        void rewardsGroupFromZeroWhenNoBalanceRow() {
            when(groupPointRepository.existsByGroupId(12L)).thenReturn(false);
            when(groupPointRepository.findWithLockByGroupId(12L)).thenReturn(Optional.of(GroupPoint.init(12L)));

            groupPointService.reward(12L, 30, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");

            verify(groupPointRepository).insertZeroBalanceIfAbsent(eq(12L), any(LocalDateTime.class));
            ArgumentCaptor<GroupPointHistory> captor = ArgumentCaptor.forClass(GroupPointHistory.class);
            verify(groupPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(30);
        }

        @Test
        @DisplayName("그룹 포인트도 잔액 부족이면 POINT_INSUFFICIENT")
        void throwsWhenGroupBalanceInsufficient() {
            when(groupPointRepository.findWithLockByGroupId(12L)).thenReturn(Optional.of(groupPoint(1000)));

            assertThatThrownBy(
                            () -> groupPointService.deduct(12L, 3000, GroupPointReason.BACKGROUND_PURCHASE, "루프탑 운동장"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_INSUFFICIENT);
        }

        @Test
        @DisplayName("amount가 0 이하면 INVALID_INPUT_VALUE 예외가 발생하고 저장하지 않는다")
        void throwsWhenAmountIsNotPositive() {
            assertThatThrownBy(() -> groupPointService.reward(12L, 0, GroupPointReason.DAILY_ALL_COMPLETE, "오운완"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

            verify(groupPointHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getHistoryDetail - 그룹 포인트 이력 상세")
    class GetHistoryDetail {

        @Test
        @DisplayName("정상 조회하면 이력을 반환한다")
        void returnsHistory() {
            when(groupPointHistoryRepository.findById(50L)).thenReturn(Optional.of(groupHistory(50L, 12L, 100, 500)));

            var response = groupPointService.getHistoryDetail(12L, 50L);

            assertThat(response.amount()).isEqualTo(100);
            assertThat(response.balanceAfter()).isEqualTo(500);
        }

        @Test
        @DisplayName("존재하지 않으면 POINT_HISTORY_NOT_FOUND")
        void throwsWhenNotFound() {
            when(groupPointHistoryRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> groupPointService.getHistoryDetail(12L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 그룹의 이력이면 존재해도 POINT_HISTORY_NOT_FOUND")
        void throwsWhenOwnedByAnotherGroup() {
            when(groupPointHistoryRepository.findById(50L)).thenReturn(Optional.of(groupHistory(50L, 99L, 100, 500)));

            assertThatThrownBy(() -> groupPointService.getHistoryDetail(12L, 50L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }
    }
}

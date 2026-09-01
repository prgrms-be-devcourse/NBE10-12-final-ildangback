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
import com.gommit.domain.point.entity.GroupPoint;
import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.entity.UserPoint;
import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.GroupPointHistoryRepository;
import com.gommit.domain.point.repository.GroupPointRepository;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {

    private final UserPointHistoryRepository userPointHistoryRepository =
            org.mockito.Mockito.mock(UserPointHistoryRepository.class);
    private final GroupPointHistoryRepository groupPointHistoryRepository =
            org.mockito.Mockito.mock(GroupPointHistoryRepository.class);
    private final UserPointRepository userPointRepository =
            org.mockito.Mockito.mock(UserPointRepository.class);
    private final GroupPointRepository groupPointRepository =
            org.mockito.Mockito.mock(GroupPointRepository.class);
    private final PointBalanceInitializer pointBalanceInitializer =
            org.mockito.Mockito.mock(PointBalanceInitializer.class);
    private final PointService pointService =
            new PointService(
                    userPointHistoryRepository,
                    groupPointHistoryRepository,
                    userPointRepository,
                    groupPointRepository,
                    pointBalanceInitializer);

    private UserPoint userPoint(int balance) {
        UserPoint point = UserPoint.init(1L);
        point.add(balance);
        return point;
    }

    private GroupPoint groupPoint(int balance) {
        GroupPoint point = GroupPoint.init(12L);
        point.add(balance);
        return point;
    }

    private UserPointHistory userHistory(Long id, Long userId, int amount, int balanceAfter) {
        UserPointHistory history =
                UserPointHistory.of(
                        userId, null, "오운완", amount, UserPointReason.CHECK_IN, balanceAfter);
        ReflectionTestUtils.setField(history, "id", id);
        return history;
    }

    @Nested
    @DisplayName("reward - 개인 포인트 지급")
    class Reward {

        @Test
        @DisplayName("잔액 행이 없으면 0에서 시작해서 지급된 만큼 잔액이 쌓인다")
        void rewardsFromZeroWhenNoBalanceRow() {
            // given: 잠금 조회는 처음엔 비어있고, 초기화 후 재조회하면 새 행이 온다
            when(userPointRepository.findWithLockByUserId(1L))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(UserPoint.init(1L)));

            // when
            pointService.reward(1L, 32L, 40, UserPointReason.CHECK_IN, "오운완");

            // then
            verify(pointBalanceInitializer).createUserPointIfAbsent(1L);
            ArgumentCaptor<UserPointHistory> captor =
                    ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(40);
            assertThat(captor.getValue().getAmount()).isEqualTo(40);
        }

        @Test
        @DisplayName("기존 잔액이 있으면 그 위에 더해서 지급된다")
        void rewardsOnTopOfExistingBalance() {
            // given
            when(userPointRepository.findWithLockByUserId(1L))
                    .thenReturn(Optional.of(userPoint(1000)));

            // when
            pointService.reward(1L, 32L, 40, UserPointReason.CHECK_IN, "오운완");

            // then
            ArgumentCaptor<UserPointHistory> captor =
                    ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(1040);
        }
    }

    @Nested
    @DisplayName("deduct - 개인 포인트 차감")
    class Deduct {

        @Test
        @DisplayName("잔액이 충분하면 차감되고 amount는 음수로 저장된다")
        void deductsWhenBalanceIsSufficient() {
            // given
            when(userPointRepository.findWithLockByUserId(1L))
                    .thenReturn(Optional.of(userPoint(1000)));

            // when
            pointService.deduct(1L, 300, UserPointReason.ITEM_PURCHASE, "핑크 왕리본");

            // then
            ArgumentCaptor<UserPointHistory> captor =
                    ArgumentCaptor.forClass(UserPointHistory.class);
            verify(userPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getAmount()).isEqualTo(-300);
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(700);
        }

        @Test
        @DisplayName("잔액이 부족하면 INSUFFICIENT_POINTS 예외가 발생하고 저장하지 않는다")
        void throwsWhenBalanceIsInsufficient() {
            // given
            when(userPointRepository.findWithLockByUserId(1L))
                    .thenReturn(Optional.of(userPoint(100)));

            // when & then
            assertThatThrownBy(
                            () ->
                                    pointService.deduct(
                                            1L, 300, UserPointReason.ITEM_PURCHASE, "핑크 왕리본"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_POINTS);

            verify(userPointHistoryRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("rewardGroup / deductGroup - 그룹 포인트")
    class GroupPointTest {

        @Test
        @DisplayName("그룹 포인트도 기존 잔액 위에 지급된다")
        void rewardsGroup() {
            // given
            when(groupPointRepository.findWithLockByGroupId(12L))
                    .thenReturn(Optional.of(groupPoint(8420)));

            // when
            pointService.rewardGroup(12L, 30, GroupPointReason.DAILY_ALL_COMPLETE, "오운완");

            // then
            ArgumentCaptor<GroupPointHistory> captor =
                    ArgumentCaptor.forClass(GroupPointHistory.class);
            verify(groupPointHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getBalanceAfter()).isEqualTo(8450);
        }

        @Test
        @DisplayName("그룹 포인트도 잔액 부족이면 INSUFFICIENT_POINTS")
        void throwsWhenGroupBalanceInsufficient() {
            // given
            when(groupPointRepository.findWithLockByGroupId(12L))
                    .thenReturn(Optional.of(groupPoint(1000)));

            // when & then
            assertThatThrownBy(
                            () ->
                                    pointService.deductGroup(
                                            12L,
                                            3000,
                                            GroupPointReason.BACKGROUND_PURCHASE,
                                            "루프탑 운동장"))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INSUFFICIENT_POINTS);
        }
    }

    @Nested
    @DisplayName("getMyBalance - 개인 포인트 잔액 조회")
    class GetMyBalance {

        @Test
        @DisplayName("현재잔액·이번달적립·이번달차감·누적적립을 함께 반환한다")
        void returnsBalanceSummary() {
            // given
            when(userPointRepository.findByUserId(1L)).thenReturn(Optional.of(userPoint(1240)));
            when(userPointHistoryRepository.sumEarnedFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(520);
            when(userPointHistoryRepository.sumSpentFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(300);
            when(userPointHistoryRepository.sumEarnedAll(1L)).thenReturn(4860);

            // when
            PointBalanceResponse response = pointService.getMyBalance(1L);

            // then
            assertThat(response.balance()).isEqualTo(1240);
            assertThat(response.monthlyEarned()).isEqualTo(520);
            assertThat(response.monthlySpent()).isEqualTo(300);
            assertThat(response.totalEarned()).isEqualTo(4860);
        }

        @Test
        @DisplayName("잔액 행이 아직 없으면 0으로 반환한다")
        void returnsZeroWhenNoBalanceRow() {
            // given
            when(userPointRepository.findByUserId(eq(1L))).thenReturn(Optional.empty());
            when(userPointHistoryRepository.sumEarnedFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(userPointHistoryRepository.sumSpentFrom(any(), any(LocalDateTime.class)))
                    .thenReturn(0);
            when(userPointHistoryRepository.sumEarnedAll(1L)).thenReturn(0);

            // when
            PointBalanceResponse response = pointService.getMyBalance(1L);

            // then
            assertThat(response.balance()).isZero();
        }
    }

    @Nested
    @DisplayName("getMyHistories - 개인 포인트 이력 커서 조회")
    class GetMyHistories {

        @Test
        @DisplayName("size보다 한 건 더 조회되면 hasNext=true, nextCursor는 잘린 마지막 항목 id다")
        void returnsHasNextTrueWhenMoreRowsExist() {
            // given: size=2인데 3건이 온 경우
            List<UserPointHistory> rows =
                    List.of(
                            userHistory(3L, 1L, 40, 300),
                            userHistory(2L, 1L, 40, 260),
                            userHistory(1L, 1L, 40, 220));
            when(userPointHistoryRepository.findHistories(
                            any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(rows);

            // when
            SliceResponse<?> result =
                    pointService.getMyHistories(
                            1L, PeriodFilter.ALL, PointChangeType.ALL, null, null, 2);

            // then
            assertThat(result.content()).hasSize(2);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.nextCursor()).isEqualTo(2L);
        }

        @Test
        @DisplayName("size만큼만 조회되면 hasNext=false, nextCursor=null이다")
        void returnsHasNextFalseWhenNoMoreRows() {
            // given
            List<UserPointHistory> rows = List.of(userHistory(1L, 1L, 40, 300));
            when(userPointHistoryRepository.findHistories(
                            any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(rows);

            // when
            SliceResponse<?> result =
                    pointService.getMyHistories(
                            1L, PeriodFilter.ALL, PointChangeType.ALL, null, null, 20);

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }
    }

    @Nested
    @DisplayName("getMyHistoryDetail - 개인 포인트 이력 상세")
    class GetMyHistoryDetail {

        @Test
        @DisplayName("존재하지 않으면 POINT_HISTORY_NOT_FOUND")
        void throwsWhenNotFound() {
            // given
            when(userPointHistoryRepository.findById(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> pointService.getMyHistoryDetail(1L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }

        @Test
        @DisplayName("다른 사용자의 이력이면 존재해도 POINT_HISTORY_NOT_FOUND(본인 것만 조회 가능)")
        void throwsWhenOwnedByAnotherUser() {
            // given
            when(userPointHistoryRepository.findById(105L))
                    .thenReturn(Optional.of(userHistory(105L, 2L, 40, 1240)));

            // when & then
            assertThatThrownBy(() -> pointService.getMyHistoryDetail(1L, 105L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.POINT_HISTORY_NOT_FOUND);
        }
    }
}

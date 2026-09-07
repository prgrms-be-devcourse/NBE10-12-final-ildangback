package com.gommit.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.record.dto.response.ChallengeMergeOverviewResponse;
import com.gommit.domain.record.dto.response.FinalMergeDetailResponse;
import com.gommit.domain.record.dto.response.MergeSummaryResponse;
import com.gommit.domain.record.dto.response.MergeType;
import com.gommit.domain.record.dto.response.MonthlyMergeDetailResponse;
import com.gommit.domain.record.dto.response.MyMonthlyMergeResponse;
import com.gommit.domain.record.entity.FinalMerge;
import com.gommit.domain.record.entity.FinalMergeResult;
import com.gommit.domain.record.entity.MonthlyMerge;
import com.gommit.domain.record.entity.MonthlyMergeResult;
import com.gommit.domain.record.repository.FinalMergeRepository;
import com.gommit.domain.record.repository.FinalMergeResultRepository;
import com.gommit.domain.record.repository.MonthlyMergeRepository;
import com.gommit.domain.record.repository.MonthlyMergeResultRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecordQueryServiceTest {

    @Mock
    private MonthlyMergeRepository monthlyMergeRepository;

    @Mock
    private MonthlyMergeResultRepository monthlyMergeResultRepository;

    @Mock
    private FinalMergeRepository finalMergeRepository;

    @Mock
    private FinalMergeResultRepository finalMergeResultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    private final ChallengeMergeCycleCalculator cycleCalculator = new ChallengeMergeCycleCalculator();

    private RecordQueryService recordQueryService;

    @BeforeEach
    void setUp() {
        recordQueryService = new RecordQueryService(
                monthlyMergeRepository,
                monthlyMergeResultRepository,
                finalMergeRepository,
                finalMergeResultRepository,
                userRepository,
                challengeRepository,
                challengeMemberRepository,
                challengeGroupRepository,
                cycleCalculator);
    }

    private User user(Long id, String nickname) {
        User user = new User("user" + id + "@example.com", "encoded", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private MonthlyMerge monthlyMerge(Long id, Long challengeId, int seqNo) {
        MonthlyMerge merge = MonthlyMerge.create(
                challengeId,
                seqNo,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 9, 18),
                30,
                135,
                90,
                LocalDateTime.of(2026, 9, 18, 4, 0));
        ReflectionTestUtils.setField(merge, "id", id);
        return merge;
    }

    private MonthlyMergeResult monthlyMergeResult(Long id, Long monthlyMergeId, Long userId) {
        MonthlyMergeResult result = MonthlyMergeResult.of(monthlyMergeId, userId, 1, 90, 27, 27, 12, 5200, 20);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    private FinalMerge finalMerge(Long id, Long challengeId) {
        FinalMerge merge = FinalMerge.create(
                challengeId,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 8, 31),
                184,
                742,
                84,
                LocalDateTime.of(2026, 8, 31, 4, 0));
        ReflectionTestUtils.setField(merge, "id", id);
        return merge;
    }

    private FinalMergeResult finalMergeResult(Long id, Long finalMergeId, Long userId) {
        FinalMergeResult result = FinalMergeResult.of(finalMergeId, userId, 1, 92, 169, 169, 47, 6760, 23);
        ReflectionTestUtils.setField(result, "id", id);
        return result;
    }

    // Challenge/ChallengeMember/ChallengeGroup은 protected 기본 생성자만 있어서(다른 패키지)
    // 리플렉션으로 만든다.
    private Challenge challenge(Long id, Long groupId, LocalDate startDate, LocalDate endDate) {
        try {
            Constructor<Challenge> constructor = Challenge.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Challenge challenge = constructor.newInstance();
            ReflectionTestUtils.setField(challenge, "id", id);
            ReflectionTestUtils.setField(challenge, "groupId", groupId);
            ReflectionTestUtils.setField(challenge, "startDate", startDate);
            ReflectionTestUtils.setField(challenge, "endDate", endDate);
            return challenge;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ChallengeGroup challengeGroup(Long id, String name, GroupCategory category) {
        try {
            Constructor<ChallengeGroup> constructor = ChallengeGroup.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ChallengeGroup group = constructor.newInstance();
            ReflectionTestUtils.setField(group, "id", id);
            ReflectionTestUtils.setField(group, "name", name);
            ReflectionTestUtils.setField(group, "category", category);
            return group;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ChallengeMember challengeMember(Challenge challenge) {
        try {
            Constructor<ChallengeMember> constructor = ChallengeMember.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ChallengeMember member = constructor.newInstance();
            ReflectionTestUtils.setField(member, "challenge", challenge);
            return member;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("getMergeList - 머지 목록 조회")
    class GetMergeList {

        @Test
        @DisplayName("최종 머지가 있으면 맨 위에, 그 뒤로 월간 머지가 최신 회차 순으로 온다")
        void returnsFinalMergeFirstThenMonthlyMergesDesc() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            when(finalMergeRepository.findByChallengeId(1L)).thenReturn(Optional.of(finalMerge(100L, 1L)));
            when(monthlyMergeRepository.findByChallengeIdOrderBySeqNoDesc(1L))
                    .thenReturn(List.of(monthlyMerge(4L, 1L, 4), monthlyMerge(3L, 1L, 3)));

            List<MergeSummaryResponse> result = recordQueryService.getMergeList(1L, 1L);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).type()).isEqualTo(MergeType.FINAL);
            assertThat(result.get(1).type()).isEqualTo(MergeType.MONTHLY);
            assertThat(result.get(1).seqNo()).isEqualTo(4);
            assertThat(result.get(2).seqNo()).isEqualTo(3);
        }

        @Test
        @DisplayName("최종 머지가 없으면 월간 머지만 반환한다")
        void returnsOnlyMonthlyMergesWhenNoFinalMerge() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            when(finalMergeRepository.findByChallengeId(1L)).thenReturn(Optional.empty());
            when(monthlyMergeRepository.findByChallengeIdOrderBySeqNoDesc(1L))
                    .thenReturn(List.of(monthlyMerge(1L, 1L, 1)));

            List<MergeSummaryResponse> result = recordQueryService.getMergeList(1L, 1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).type()).isEqualTo(MergeType.MONTHLY);
        }

        @Test
        @DisplayName("그 챌린지의 ACTIVE 멤버가 아니면 ACCESS_DENIED")
        void throwsWhenNotActiveMember() {
            when(challengeMemberRepository.existsActiveMember(1L, 99L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> recordQueryService.getMergeList(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("getMonthlyMergeDetail - 월간 머지 상세 조회")
    class GetMonthlyMergeDetail {

        @Test
        @DisplayName("참여자 결과를 순위순으로 포함해서 반환한다")
        void returnsDetailWithParticipants() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            MonthlyMerge merge = monthlyMerge(10L, 1L, 4);
            when(monthlyMergeRepository.findByChallengeIdAndSeqNo(1L, 4)).thenReturn(Optional.of(merge));
            when(monthlyMergeResultRepository.findByMonthlyMergeIdOrderByRanking(10L))
                    .thenReturn(List.of(monthlyMergeResult(1L, 10L, 5L)));
            when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(user(5L, "라니")));

            MonthlyMergeDetailResponse response = recordQueryService.getMonthlyMergeDetail(1L, 4, 1L);

            assertThat(response.seqNo()).isEqualTo(4);
            assertThat(response.participants()).hasSize(1);
            assertThat(response.participants().get(0).userId()).isEqualTo(5L);
            assertThat(response.participants().get(0).nickname()).isEqualTo("라니");
        }

        @Test
        @DisplayName("존재하지 않으면 MONTHLY_MERGE_NOT_FOUND")
        void throwsWhenNotFound() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            when(monthlyMergeRepository.findByChallengeIdAndSeqNo(1L, 99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recordQueryService.getMonthlyMergeDetail(1L, 99, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.MONTHLY_MERGE_NOT_FOUND);
        }

        @Test
        @DisplayName("그 챌린지의 ACTIVE 멤버가 아니면 ACCESS_DENIED")
        void throwsWhenNotActiveMember() {
            when(challengeMemberRepository.existsActiveMember(1L, 99L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> recordQueryService.getMonthlyMergeDetail(1L, 4, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("getFinalMergeDetail - 최종 머지 상세 조회")
    class GetFinalMergeDetail {

        @Test
        @DisplayName("참여자 결과를 순위순으로 포함해서 반환한다")
        void returnsDetailWithParticipants() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            FinalMerge merge = finalMerge(100L, 1L);
            when(finalMergeRepository.findByChallengeId(1L)).thenReturn(Optional.of(merge));
            when(finalMergeResultRepository.findByFinalMergeIdOrderByRanking(100L))
                    .thenReturn(List.of(finalMergeResult(1L, 100L, 5L)));
            when(userRepository.findAllById(List.of(5L))).thenReturn(List.of(user(5L, "라니")));

            FinalMergeDetailResponse response = recordQueryService.getFinalMergeDetail(1L, 1L);

            assertThat(response.challengeId()).isEqualTo(1L);
            assertThat(response.participants()).hasSize(1);
            assertThat(response.participants().get(0).nickname()).isEqualTo("라니");
        }

        @Test
        @DisplayName("존재하지 않으면 FINAL_MERGE_NOT_FOUND")
        void throwsWhenNotFound() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            when(finalMergeRepository.findByChallengeId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recordQueryService.getFinalMergeDetail(1L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FINAL_MERGE_NOT_FOUND);
        }

        @Test
        @DisplayName("그 챌린지의 ACTIVE 멤버가 아니면 ACCESS_DENIED")
        void throwsWhenNotActiveMember() {
            when(challengeMemberRepository.existsActiveMember(1L, 99L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> recordQueryService.getFinalMergeDetail(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("getMyMonthlyMergeArchive - 내 월간 머지 아카이브 커서 조회")
    class GetMyMonthlyMergeArchive {

        @Test
        @DisplayName("size보다 한 건 더 조회되면 hasNext=true이고, 부모 머지 정보가 같이 채워진다")
        void returnsHasNextTrueWithMergeInfoJoined() {
            MonthlyMergeResult r1 = monthlyMergeResult(3L, 10L, 1L);
            MonthlyMergeResult r2 = monthlyMergeResult(2L, 9L, 1L);
            when(monthlyMergeResultRepository.findHistoriesByUserId(any(), any(), any()))
                    .thenReturn(List.of(r1, r2));
            when(monthlyMergeRepository.findAllById(any()))
                    .thenReturn(List.of(monthlyMerge(10L, 1L, 4), monthlyMerge(9L, 1L, 3)));

            SliceResponse<MyMonthlyMergeResponse> result = recordQueryService.getMyMonthlyMergeArchive(1L, null, 1);

            assertThat(result.content()).hasSize(1);
            assertThat(result.hasNext()).isTrue();
            assertThat(result.content().get(0).seqNo()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("getChallengeMergeOverview - 챌린지 머지 진행 현황")
    class GetChallengeMergeOverview {

        @Test
        @DisplayName("완료된 회차가 있고 아직 안 끝났으면 진행 중인 회차 번호/날짜를 계산해서 채운다")
        void fillsCurrentCycleWhenInProgress() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            LocalDate today = LocalDate.now();
            LocalDate start = today.minusDays(19); // 지금이 1회차 20일째가 되도록(경과일 19 + 1)
            when(challengeRepository.findById(1L))
                    .thenReturn(Optional.of(challenge(1L, 10L, start, start.plusDays(179))));
            when(challengeGroupRepository.findById(10L))
                    .thenReturn(Optional.of(challengeGroup(10L, "오운완", GroupCategory.EXERCISE)));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(false);

            ChallengeMergeOverviewResponse response = recordQueryService.getChallengeMergeOverview(1L, 1L);

            assertThat(response.groupName()).isEqualTo("오운완");
            assertThat(response.category()).isEqualTo("EXERCISE");
            // 180일 챌린지는 6사이클인데 마지막 사이클은 최종 머지 몫이라 월간 머지는 5개다.
            assertThat(response.totalMergeCount()).isEqualTo(5);
            assertThat(response.completedMergeCount()).isZero();
            assertThat(response.currentSeqNo()).isEqualTo(1);
            assertThat(response.currentCycleDay()).isEqualTo(20);
        }

        @Test
        @DisplayName("최종 머지가 이미 있으면 진행 중인 회차는 null이다")
        void noCurrentCycleWhenFinalMergeExists() {
            when(challengeMemberRepository.existsActiveMember(1L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            LocalDate start = LocalDate.of(2026, 3, 1);
            when(challengeRepository.findById(1L))
                    .thenReturn(Optional.of(challenge(1L, 10L, start, start.plusDays(179))));
            when(challengeGroupRepository.findById(10L))
                    .thenReturn(Optional.of(challengeGroup(10L, "오운완", GroupCategory.EXERCISE)));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(5);
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(true);

            ChallengeMergeOverviewResponse response = recordQueryService.getChallengeMergeOverview(1L, 1L);

            assertThat(response.hasFinalMerge()).isTrue();
            assertThat(response.currentSeqNo()).isNull();
            assertThat(response.currentCycleDay()).isNull();
        }

        @Test
        @DisplayName("존재하지 않으면 RESOURCE_NOT_FOUND")
        void throwsWhenNotFound() {
            when(challengeMemberRepository.existsActiveMember(999L, 1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(true);
            when(challengeRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> recordQueryService.getChallengeMergeOverview(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("그 챌린지의 ACTIVE 멤버가 아니면 ACCESS_DENIED")
        void throwsWhenNotActiveMember() {
            when(challengeMemberRepository.existsActiveMember(1L, 99L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> recordQueryService.getChallengeMergeOverview(1L, 99L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }

    @Nested
    @DisplayName("getMyChallengeMergeOverviews - 내가 속한 챌린지별 머지 진행 현황")
    class GetMyChallengeMergeOverviews {

        @Test
        @DisplayName("내가 속한 챌린지 수만큼 요약을 반환한다")
        void returnsOverviewPerMembership() {
            Challenge challengeA = challenge(1L, 10L, LocalDate.of(2026, 8, 20), LocalDate.of(2027, 2, 15));
            Challenge challengeB = challenge(2L, 20L, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 29));
            ChallengeGroup groupA = challengeGroup(10L, "오운완", GroupCategory.EXERCISE);
            ChallengeGroup groupB = challengeGroup(20L, "매일 20분 독서", GroupCategory.READING);
            when(challengeMemberRepository.findAllByUserId(1L))
                    .thenReturn(List.of(challengeMember(challengeA), challengeMember(challengeB)));
            when(challengeRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(challengeA, challengeB));
            when(challengeGroupRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(groupA, groupB));
            when(monthlyMergeRepository.findAllByChallengeIdIn(List.of(1L, 2L)))
                    .thenReturn(List.of(
                            monthlyMerge(1L, 1L, 1),
                            monthlyMerge(2L, 1L, 2),
                            monthlyMerge(3L, 1L, 3),
                            monthlyMerge(4L, 2L, 1),
                            monthlyMerge(5L, 2L, 2),
                            monthlyMerge(6L, 2L, 3)));
            when(finalMergeRepository.findAllByChallengeIdIn(List.of(1L, 2L))).thenReturn(List.of(finalMerge(1L, 2L)));

            List<ChallengeMergeOverviewResponse> result = recordQueryService.getMyChallengeMergeOverviews(1L);

            assertThat(result).hasSize(2);
            assertThat(result)
                    .extracting(ChallengeMergeOverviewResponse::challengeId)
                    .containsExactly(1L, 2L);
            assertThat(result.get(0).groupName()).isEqualTo("오운완");
            assertThat(result.get(1).hasFinalMerge()).isTrue();
        }
    }
}

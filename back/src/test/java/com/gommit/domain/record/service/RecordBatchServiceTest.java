package com.gommit.domain.record.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.CheckInRepository.UserBusinessDate;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.point.config.PointProperties;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.repository.UserPointHistoryRepository;
import com.gommit.domain.point.repository.UserPointHistoryRepository.UserEarnedAmount;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.record.entity.FinalMerge;
import com.gommit.domain.record.entity.FinalMergeResult;
import com.gommit.domain.record.entity.MonthlyMerge;
import com.gommit.domain.record.repository.FinalMergeRepository;
import com.gommit.domain.record.repository.FinalMergeResultRepository;
import com.gommit.domain.record.repository.MonthlyMergeRepository;
import com.gommit.domain.record.repository.MonthlyMergeResultRepository;
import com.gommit.global.time.BusinessClock;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
class RecordBatchServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private ChallengeGroupRepository challengeGroupRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private UserPointHistoryRepository userPointHistoryRepository;

    @Mock
    private MonthlyMergeRepository monthlyMergeRepository;

    @Mock
    private MonthlyMergeResultRepository monthlyMergeResultRepository;

    @Mock
    private FinalMergeRepository finalMergeRepository;

    @Mock
    private FinalMergeResultRepository finalMergeResultRepository;

    @Mock
    private PersonalPointService personalPointService;

    private RecordBatchService recordBatchService;
    private long nextGeneratedId;

    @BeforeEach
    void setUp() {
        PointProperties pointProperties = new PointProperties(10, 5, 150, 0);
        BusinessClock businessClock = new BusinessClock(
                Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
        recordBatchService = new RecordBatchService(
                challengeRepository,
                challengeMemberRepository,
                challengeGroupRepository,
                checkInRepository,
                userPointHistoryRepository,
                monthlyMergeRepository,
                monthlyMergeResultRepository,
                finalMergeRepository,
                finalMergeResultRepository,
                personalPointService,
                pointProperties,
                new ChallengeMergeCycleCalculator(),
                new RecordCompletionCalculator(),
                businessClock);

        // save()는 실제 DB에서는 IDENTITY 전략으로 엔티티에 id를 채워 넣는다(같은 인스턴스를
        // 리턴) - 목에서도 같은 동작을 흉내내야 이후 코드의 merge.getId() 호출이 유효하다.
        lenient().when(monthlyMergeRepository.save(any())).thenAnswer(inv -> {
            MonthlyMerge merge = inv.getArgument(0);
            ReflectionTestUtils.setField(merge, "id", ++nextGeneratedId);
            return merge;
        });
        lenient().when(finalMergeRepository.save(any())).thenAnswer(inv -> {
            FinalMerge merge = inv.getArgument(0);
            ReflectionTestUtils.setField(merge, "id", ++nextGeneratedId);
            return merge;
        });
    }

    private Challenge challenge(Long id, LocalDate startDate, LocalDate endDate) {
        Challenge challenge = Challenge.builder()
                .groupId(12L)
                .seqNo(1)
                .startDate(startDate)
                .endDate(endDate)
                .frequencyType(FrequencyType.DAILY)
                .frequencyValue(null)
                .daysOfWeek(null)
                .dailyCheckInCount(1)
                .requiredDayCount((int) (endDate.toEpochDay() - startDate.toEpochDay() + 1))
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(true)
                .build();
        challenge.activate();
        setBaseFields(challenge, id);
        return challenge;
    }

    private ChallengeMember member(Long id, Challenge challenge, Long userId) {
        ChallengeMember member = ChallengeMember.builder()
                .challenge(challenge)
                .userId(userId)
                .role(ChallengeMemberRole.MEMBER)
                .build();
        setBaseFields(member, id);
        return member;
    }

    private void setBaseFields(Object target, Long id) {
        ReflectionTestUtils.setField(target, "id", id);
        ReflectionTestUtils.setField(target, "createdAt", LocalDateTime.of(2026, 1, 1, 4, 0));
        ReflectionTestUtils.setField(target, "updatedAt", LocalDateTime.of(2026, 1, 1, 4, 0));
    }

    private UserBusinessDate businessDate(Long userId, LocalDate date) {
        return new UserBusinessDate() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public LocalDate getBusinessDate() {
                return date;
            }
        };
    }

    private UserEarnedAmount earnedAmount(Long userId, int amount) {
        return new UserEarnedAmount() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Integer getAmount() {
                return amount;
            }
        };
    }

    @Nested
    @DisplayName("generateDueMonthlyMerges - 월간 머지 생성")
    class GenerateDueMonthlyMerges {

        @Test
        @DisplayName("30일 주기가 지난 ACTIVE 챌린지는 월간 머지를 생성한다")
        void generatesMonthlyMergeWhenCycleHasPassed() {
            // given: 180일 챌린지, 오늘은 시작 후 31일째 - 1회차(0~29일)가 이미 끝났다.
            LocalDate startDate = TODAY.minusDays(31);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(179));
            ChallengeMember member1 = member(10L, challenge, 100L);
            ChallengeMember member2 = member(11L, challenge, 200L);
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);
            when(monthlyMergeRepository.existsByChallengeIdAndSeqNo(1L, 1)).thenReturn(false);
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(member1, member2));
            LocalDate periodStart = startDate;
            when(checkInRepository.findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                            eq(1L), anyList(), eq(periodStart), eq(periodStart.plusDays(29))))
                    .thenReturn(List.of(
                            businessDate(100L, periodStart),
                            businessDate(100L, periodStart.plusDays(1)),
                            businessDate(200L, periodStart)));
            when(userPointHistoryRepository.sumEarnedByChallengeIdAndUserIdInBetween(
                            eq(1L), anyList(), any(), any()))
                    .thenReturn(List.of(earnedAmount(100L, 20), earnedAmount(200L, 10)));

            // when
            recordBatchService.generateDueMonthlyMerges();

            // then
            ArgumentCaptor<MonthlyMerge> mergeCaptor = ArgumentCaptor.forClass(MonthlyMerge.class);
            verify(monthlyMergeRepository).save(mergeCaptor.capture());
            MonthlyMerge saved = mergeCaptor.getValue();
            assertThat(saved.getChallengeId()).isEqualTo(1L);
            assertThat(saved.getSeqNo()).isEqualTo(1);
            assertThat(saved.getPeriodStart()).isEqualTo(periodStart);
            assertThat(saved.getPeriodEnd()).isEqualTo(periodStart.plusDays(29));
            assertThat(saved.getTotalDays()).isEqualTo(30);
            assertThat(saved.getTotalCheckInCount()).isEqualTo(3);

            verify(monthlyMergeResultRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("오늘이 periodEnd 당일이면 아직 마감 전이라 생성하지 않는다(경계값)")
        void doesNotGenerateOnPeriodEndDayItself() {
            // given: 1회차 periodEnd가 정확히 오늘이다 - 그 businessDate는 다음날 04:00
            // 컷오프 전까지 체크인이 계속 들어올 수 있어 아직 마감된 게 아니다.
            LocalDate startDate = TODAY.minusDays(29);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(179));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);

            // when
            recordBatchService.generateDueMonthlyMerges();

            // then
            verify(monthlyMergeRepository, never()).save(any());
        }

        @Test
        @DisplayName("아직 주기가 안 끝났으면 생성하지 않는다")
        void doesNotGenerateWhenCycleNotFinished() {
            // given: 시작한 지 10일밖에 안 됨 - 1회차(0~29일)가 아직 안 끝남.
            LocalDate startDate = TODAY.minusDays(10);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(179));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);

            // when
            recordBatchService.generateDueMonthlyMerges();

            // then
            verify(monthlyMergeRepository, never()).save(any());
            verify(challengeMemberRepository, never()).findAllByChallengeIdAndStatus(any(), any());
        }

        @Test
        @DisplayName("이미 발행된 회차는 건너뛰고 그 다음 회차만 새로 생성한다(멱등성)")
        void skipsAlreadyPublishedSeqNoAndGeneratesNextOne() {
            // given: 90일 챌린지, 오늘은 시작 후 65일째 - 1회차/2회차 모두 기간이 끝났는데
            // 1회차는 이미 발행돼 있고 2회차만 아직이다.
            LocalDate startDate = TODAY.minusDays(65);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(89));
            ChallengeMember member = member(10L, challenge, 100L);
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);
            when(monthlyMergeRepository.existsByChallengeIdAndSeqNo(1L, 1)).thenReturn(true);
            when(monthlyMergeRepository.existsByChallengeIdAndSeqNo(1L, 2)).thenReturn(false);
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(member));
            when(checkInRepository.findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                            any(), anyList(), any(), any()))
                    .thenReturn(List.of());
            when(userPointHistoryRepository.sumEarnedByChallengeIdAndUserIdInBetween(any(), anyList(), any(), any()))
                    .thenReturn(List.of());

            // when
            recordBatchService.generateDueMonthlyMerges();

            // then
            ArgumentCaptor<MonthlyMerge> captor = ArgumentCaptor.forClass(MonthlyMerge.class);
            verify(monthlyMergeRepository).save(captor.capture());
            assertThat(captor.getValue().getSeqNo()).isEqualTo(2);
        }

        @Test
        @DisplayName("참여 멤버가 없으면 생성하지 않는다")
        void doesNotGenerateWhenNoMembers() {
            LocalDate startDate = TODAY.minusDays(31);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(179));
            when(challengeRepository.findAllByStatus(ChallengeStatus.ACTIVE)).thenReturn(List.of(challenge));
            when(monthlyMergeRepository.countByChallengeId(1L)).thenReturn(0);
            when(monthlyMergeRepository.existsByChallengeIdAndSeqNo(1L, 1)).thenReturn(false);
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of());

            recordBatchService.generateDueMonthlyMerges();

            verify(monthlyMergeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("generateFinalMerge - 최종 머지 생성 및 완주 보너스 지급")
    class GenerateFinalMerge {

        @Test
        @DisplayName("완료율 내림차순으로 랭킹이 매겨지고, 50% 이상인 사람만 완주 보너스를 받는다")
        void ranksByCompletionRateAndPaysBonusOnlyAboveThreshold() {
            // given: 10일짜리 챌린지. userA는 6일 인증(60%, 보너스 O), userB는 2일 인증(20%, 보너스 X).
            LocalDate startDate = TODAY.minusDays(20);
            LocalDate endDate = startDate.plusDays(9);
            Challenge challenge = challenge(1L, startDate, endDate);
            ChallengeMember memberA = member(10L, challenge, 100L);
            ChallengeMember memberB = member(11L, challenge, 200L);
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(false);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(memberA, memberB));
            List<UserBusinessDate> dates = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                dates.add(businessDate(100L, startDate.plusDays(i)));
            }
            for (int i = 0; i < 2; i++) {
                dates.add(businessDate(200L, startDate.plusDays(i)));
            }
            when(checkInRepository.findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                            eq(1L), anyList(), eq(startDate), eq(endDate)))
                    .thenReturn(dates);
            when(userPointHistoryRepository.sumEarnedByChallengeIdAndUserIdInBetween(eq(1L), anyList(), any(), any()))
                    .thenReturn(List.of(earnedAmount(100L, 60), earnedAmount(200L, 20)));
            when(challengeGroupRepository.findNameById(12L)).thenReturn(Optional.of("오운완 모임"));

            // when
            recordBatchService.generateFinalMerge(1L);

            // then
            ArgumentCaptor<FinalMerge> mergeCaptor = ArgumentCaptor.forClass(FinalMerge.class);
            verify(finalMergeRepository).save(mergeCaptor.capture());
            assertThat(mergeCaptor.getValue().getTotalDays()).isEqualTo(10);
            assertThat(mergeCaptor.getValue().getTotalCheckInCount()).isEqualTo(8);

            ArgumentCaptor<FinalMergeResult> resultCaptor = ArgumentCaptor.forClass(FinalMergeResult.class);
            verify(finalMergeResultRepository, times(2)).save(resultCaptor.capture());
            List<FinalMergeResult> results = resultCaptor.getAllValues();
            FinalMergeResult resultA = results.stream()
                    .filter(r -> r.getUserId().equals(100L))
                    .findFirst()
                    .orElseThrow();
            FinalMergeResult resultB = results.stream()
                    .filter(r -> r.getUserId().equals(200L))
                    .findFirst()
                    .orElseThrow();
            assertThat(resultA.getCompletionRate()).isEqualTo(60);
            assertThat(resultA.getRanking()).isEqualTo(1);
            assertThat(resultA.getEarnedPoints()).isEqualTo(60);
            assertThat(resultB.getCompletionRate()).isEqualTo(20);
            assertThat(resultB.getRanking()).isEqualTo(2);
            assertThat(resultB.getEarnedPoints()).isEqualTo(20);

            verify(personalPointService).reward(100L, 1L, 150, UserPointReason.CHALLENGE_BONUS, "오운완 모임");
            verify(personalPointService, never()).reward(eq(200L), any(), anyInt(), any(), any());
        }

        @Test
        @DisplayName("완료율이 같으면 순위는 공동 1위지만, 정렬 순서는 총 인증횟수 내림차순이다")
        void tiedCompletionRateSharesRankButOrdersByCheckInCount() {
            // given: userA(200L)와 userB(100L) 둘 다 완료율 50%(5일)로 랭킹은 공동 1위지만,
            // userA가 하루 여러 번 더 인증해서 totalCheckInCount가 더 많다 - 정렬 순서는
            // (동률 랭킹 안에서의 표시 순서를 결정하기 위해) userA가 앞에 온다.
            LocalDate startDate = TODAY.minusDays(20);
            LocalDate endDate = startDate.plusDays(9);
            Challenge challenge = challenge(1L, startDate, endDate);
            ChallengeMember memberA = member(10L, challenge, 200L);
            ChallengeMember memberB = member(11L, challenge, 100L);
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(false);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(memberA, memberB));
            List<UserBusinessDate> dates = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                dates.add(businessDate(200L, startDate.plusDays(i)));
                dates.add(businessDate(200L, startDate.plusDays(i))); // 하루 두 번 인증
                dates.add(businessDate(100L, startDate.plusDays(i)));
            }
            when(checkInRepository.findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                            eq(1L), anyList(), eq(startDate), eq(endDate)))
                    .thenReturn(dates);
            when(userPointHistoryRepository.sumEarnedByChallengeIdAndUserIdInBetween(eq(1L), anyList(), any(), any()))
                    .thenReturn(List.of());
            when(challengeGroupRepository.findNameById(12L)).thenReturn(Optional.of("오운완 모임"));

            // when
            recordBatchService.generateFinalMerge(1L);

            // then
            ArgumentCaptor<FinalMergeResult> resultCaptor = ArgumentCaptor.forClass(FinalMergeResult.class);
            verify(finalMergeResultRepository, times(2)).save(resultCaptor.capture());
            List<FinalMergeResult> results = resultCaptor.getAllValues();
            FinalMergeResult result200 = results.stream()
                    .filter(r -> r.getUserId().equals(200L))
                    .findFirst()
                    .orElseThrow();
            FinalMergeResult result100 = results.stream()
                    .filter(r -> r.getUserId().equals(100L))
                    .findFirst()
                    .orElseThrow();
            assertThat(result200.getCompletionRate()).isEqualTo(result100.getCompletionRate());
            // 완료율이 같으므로 랭킹(등수 표기)은 공동 1위 - totalCheckInCount는 순위가
            // 아니라 저장 순서(표시 순서)에만 영향을 준다.
            assertThat(result200.getRanking()).isEqualTo(1);
            assertThat(result100.getRanking()).isEqualTo(1);
            assertThat(results).extracting(FinalMergeResult::getUserId).containsExactly(200L, 100L);
        }

        @Test
        @DisplayName("완료율이 정확히 같은 완료율/인증횟수면 userId가 작은 쪽이 앞순위다")
        void ranksExactTiesByUserId() {
            LocalDate startDate = TODAY.minusDays(20);
            LocalDate endDate = startDate.plusDays(9);
            Challenge challenge = challenge(1L, startDate, endDate);
            ChallengeMember memberHighId = member(10L, challenge, 200L);
            ChallengeMember memberLowId = member(11L, challenge, 100L);
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(false);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of(memberHighId, memberLowId));
            when(checkInRepository.findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
                            eq(1L), anyList(), eq(startDate), eq(endDate)))
                    .thenReturn(List.of());
            when(userPointHistoryRepository.sumEarnedByChallengeIdAndUserIdInBetween(eq(1L), anyList(), any(), any()))
                    .thenReturn(List.of());
            when(challengeGroupRepository.findNameById(12L)).thenReturn(Optional.of("오운완 모임"));

            recordBatchService.generateFinalMerge(1L);

            ArgumentCaptor<FinalMergeResult> resultCaptor = ArgumentCaptor.forClass(FinalMergeResult.class);
            verify(finalMergeResultRepository, times(2)).save(resultCaptor.capture());
            List<FinalMergeResult> results = resultCaptor.getAllValues();
            FinalMergeResult result100 = results.stream()
                    .filter(r -> r.getUserId().equals(100L))
                    .findFirst()
                    .orElseThrow();
            FinalMergeResult result200 = results.stream()
                    .filter(r -> r.getUserId().equals(200L))
                    .findFirst()
                    .orElseThrow();
            assertThat(result100.getRanking()).isEqualTo(1);
            assertThat(result200.getRanking()).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 발행됐으면 다시 생성하지 않는다")
        void doesNotRegenerateWhenAlreadyPublished() {
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(true);

            recordBatchService.generateFinalMerge(1L);

            verify(challengeRepository, never()).findById(any());
            verify(finalMergeRepository, never()).save(any());
        }

        @Test
        @DisplayName("참여 멤버가 없으면 생성하지 않는다")
        void doesNotGenerateWhenNoMembers() {
            LocalDate startDate = TODAY.minusDays(20);
            Challenge challenge = challenge(1L, startDate, startDate.plusDays(9));
            when(finalMergeRepository.existsByChallengeId(1L)).thenReturn(false);
            when(challengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
            when(challengeMemberRepository.findAllByChallengeIdAndStatus(1L, ChallengeMemberStatus.ACTIVE))
                    .thenReturn(List.of());

            recordBatchService.generateFinalMerge(1L);

            verify(finalMergeRepository, never()).save(any());
            verify(personalPointService, never()).reward(any(), any(), anyInt(), any(), any());
        }
    }
}

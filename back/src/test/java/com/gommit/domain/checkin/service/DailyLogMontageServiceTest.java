package com.gommit.domain.checkin.service;

import static com.gommit.domain.checkin.CheckInFixture.activeMember;
import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.entity.DailyLog;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.media.DailyLogMediaStore;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.repository.DailyLogRepository;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("DailyLogMontageService")
class DailyLogMontageServiceTest {

    private static final long CHALLENGE_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 5);

    private final Challenge challenge = dailyChallenge(CHALLENGE_ID, 1);

    @Mock
    private DailyLogRepository dailyLogRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private ChallengeMemberRepository challengeMemberRepository;

    @Mock
    private CheckInMediaStore checkInMediaStore;

    @Mock
    private DailyLogMediaStore dailyLogMediaStore;

    @Mock
    private DailyLogMontageBuilder montageBuilder;

    @InjectMocks
    private DailyLogMontageService service;

    private DailyLog dailyLog;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxRounds", 8);
        dailyLog = DailyLog.create(CHALLENGE_ID, DATE);
        lenient()
                .when(dailyLogRepository.findByChallengeIdAndLogDate(CHALLENGE_ID, DATE))
                .thenReturn(Optional.of(dailyLog));
        lenient().when(checkInMediaStore.load(anyString())).thenReturn(new ByteArrayResource(new byte[] {1}));
    }

    private void snapshot(ChallengeMember... members) {
        when(challengeMemberRepository.findSnapshotMemberUserIds(
                        eq(CHALLENGE_ID), any(), any(), eq(ChallengeMemberStatus.ACTIVE)))
                .thenReturn(
                        Arrays.stream(members).map(ChallengeMember::getUserId).toList());
    }

    private void checkIns(CheckIn... checkIns) {
        when(checkInRepository.findByChallengeIdAndBusinessDate(CHALLENGE_ID, DATE))
                .thenReturn(List.of(checkIns));
    }

    private CheckIn checkIn(long userId, int roundNo) {
        return checkIn(userId, roundNo, "key-%d-%d.jpg".formatted(userId, roundNo));
    }

    private CheckIn checkIn(long userId, int roundNo, String mediaKey) {
        return new CheckIn(CHALLENGE_ID, userId, roundNo, CheckInType.PHOTO, mediaKey, MediaType.IMAGE, null, DATE);
    }

    @SuppressWarnings("unchecked")
    private List<List<Frame>> captureRounds(int expectedCellCount) {
        ArgumentCaptor<List<List<Frame>>> captor = ArgumentCaptor.forClass(List.class);
        verify(montageBuilder).build(eq(expectedCellCount), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("스냅샷 멤버를 가입순 칸에 매핑하고 회차별 슬롯을 빌더에 넘긴다")
    void mapsMembersToCellsAndPassesRounds() {
        snapshot(activeMember(10L, challenge, 100L), activeMember(20L, challenge, 200L));
        checkIns(checkIn(100L, 1), checkIn(200L, 1), checkIn(100L, 2), checkIn(200L, 2));
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.of(new byte[] {9}));
        when(dailyLogMediaStore.store(any())).thenReturn("video-key");

        service.generateMontage(CHALLENGE_ID, DATE);

        List<List<Frame>> rounds = captureRounds(2);
        assertThat(rounds).hasSize(2);
        assertThat(rounds).allSatisfy(slots -> assertThat(slots).hasSize(2).doesNotContainNull());
        assertThat(dailyLog.getVideoKey()).isEqualTo("video-key");
    }

    @Test
    @DisplayName("인증 안 한 멤버의 칸은 비어(null) 있다")
    void absentMemberCellIsNull() {
        snapshot(activeMember(10L, challenge, 100L), activeMember(20L, challenge, 200L));
        checkIns(checkIn(100L, 1)); // 200L 은 인증 안 함
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        List<List<Frame>> rounds = captureRounds(2);
        assertThat(rounds).hasSize(1);
        assertThat(rounds.get(0).get(0)).isNotNull();
        assertThat(rounds.get(0).get(1)).isNull();
    }

    @Test
    @DisplayName("회차 내 같은 유저가 중복 인증하면 먼저 조회된 것만 쓴다")
    void duplicateCheckInInRoundKeepsFirst() {
        snapshot(activeMember(10L, challenge, 100L));
        checkIns(checkIn(100L, 1, "first.jpg"), checkIn(100L, 1, "second.jpg"));
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        verify(checkInMediaStore).load("first.jpg");
        verify(checkInMediaStore, never()).load("second.jpg");
    }

    @Test
    @DisplayName("칸이 배정된 멤버가 아무도 인증하지 않은 회차는 스킵한다")
    void roundWithNoMappedUserIsSkipped() {
        snapshot(activeMember(10L, challenge, 100L));
        checkIns(checkIn(100L, 1), checkIn(999L, 2)); // round 2 는 멤버 아닌 유저뿐

        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        List<List<Frame>> rounds = captureRounds(1);
        assertThat(rounds).hasSize(1);
    }

    @Test
    @DisplayName("montage-max-rounds 를 넘는 회차는 잘린다")
    void capsRoundsAtMaxRounds() {
        ReflectionTestUtils.setField(service, "maxRounds", 2);
        snapshot(activeMember(10L, challenge, 100L));
        checkIns(checkIn(100L, 1), checkIn(100L, 2), checkIn(100L, 3), checkIn(100L, 4));
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        assertThat(captureRounds(1)).hasSize(2);
    }

    @Test
    @DisplayName("스냅샷 멤버가 6명을 넘으면 앞 6명만 칸에 배정하고 나머지 인증은 무시한다")
    void moreThanSixMembersUsesFirstSix() {
        snapshot(
                activeMember(1L, challenge, 1L),
                activeMember(2L, challenge, 2L),
                activeMember(3L, challenge, 3L),
                activeMember(4L, challenge, 4L),
                activeMember(5L, challenge, 5L),
                activeMember(6L, challenge, 6L),
                activeMember(7L, challenge, 7L));
        checkIns(checkIn(1L, 1), checkIn(7L, 1));
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        List<List<Frame>> rounds = captureRounds(6);
        assertThat(rounds.get(0)).hasSize(6);
        assertThat(rounds.get(0).get(0)).isNotNull(); // user 1 → cell 0
        assertThat(rounds.get(0).subList(1, 6)).containsOnlyNulls(); // user 7 무시
    }

    @Test
    @DisplayName("스냅샷 멤버가 없으면 빌더를 호출하지 않는다")
    void noSnapshotMembersSkips() {
        snapshot();

        service.generateMontage(CHALLENGE_ID, DATE);

        verify(montageBuilder, never()).build(anyInt(), any());
    }

    @Test
    @DisplayName("그 날 인증이 없으면 빌더를 호출하지 않는다")
    void noCheckInsSkips() {
        snapshot(activeMember(10L, challenge, 100L));
        checkIns();

        service.generateMontage(CHALLENGE_ID, DATE);

        verify(montageBuilder, never()).build(anyInt(), any());
    }

    @Test
    @DisplayName("이미 videoKey 가 있으면 아무것도 하지 않는다")
    void alreadyGeneratedSkips() {
        ReflectionTestUtils.setField(dailyLog, "videoKey", "existing");

        service.generateMontage(CHALLENGE_ID, DATE);

        verify(challengeMemberRepository, never()).findSnapshotMemberUserIds(any(), any(), any(), any());
        verify(montageBuilder, never()).build(anyInt(), any());
    }

    @Test
    @DisplayName("빌더가 빈 값을 반환하면 videoKey 는 null 로 남고 저장하지 않는다")
    void builderFailureLeavesVideoKeyNull() {
        snapshot(activeMember(10L, challenge, 100L));
        checkIns(checkIn(100L, 1));
        when(montageBuilder.build(anyInt(), any())).thenReturn(Optional.empty());

        service.generateMontage(CHALLENGE_ID, DATE);

        assertThat(dailyLog.getVideoKey()).isNull();
        verify(dailyLogMediaStore, never()).store(any());
    }
}

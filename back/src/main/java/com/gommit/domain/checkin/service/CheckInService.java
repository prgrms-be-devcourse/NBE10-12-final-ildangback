package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.challenge.service.ChallengeStreakService;
import com.gommit.domain.challenge.service.MemberCheckInResult;
import com.gommit.domain.checkin.dto.request.SubmitCheckInRequest;
import com.gommit.domain.checkin.dto.response.CheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.CheckInResponse;
import com.gommit.domain.checkin.dto.response.CheckInResultResponse;
import com.gommit.domain.checkin.dto.response.CursorPageMeta;
import com.gommit.domain.checkin.dto.response.MyCheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.MyCheckInPageMeta;
import com.gommit.domain.checkin.dto.response.MyCheckInResponse;
import com.gommit.domain.checkin.dto.response.RecentCheckInResponse;
import com.gommit.domain.checkin.dto.response.RecentCheckInResponse.RecentCheckInItem;
import com.gommit.domain.checkin.dto.response.TodayCheckInStatusResponse;
import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.policy.CheckInPolicy;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.support.CheckInPreconditions;
import com.gommit.domain.checkin.support.CheckInPreconditions.ReadDateAccess;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CheckInPreconditions preconditions;
    private final CheckInPolicy policy;
    private final ChallengeProgressCalculator progressCalculator;
    private final CheckInMediaStore mediaStore;
    private final PersonalPointService personalPointService;
    private final UserService userService;
    private final ChallengeStreakService challengeStreakService;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final BusinessClock businessClock;

    public TodayCheckInStatusResponse getTodayStatus(Long userId, Long challengeId) {
        Challenge challenge = preconditions.getActiveChallengeForActiveMember(challengeId, userId);
        LocalDate today = businessClock.today();

        int target = challenge.getDailyCheckInCount();
        int current = checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, userId, today);

        return new TodayCheckInStatusResponse(
                today,
                progressCalculator.canCheckInOn(challenge, today),
                current,
                target,
                current >= target,
                policy.allowedTypes(challenge));
    }

    @Transactional
    public CheckInResultResponse submit(Long userId, Long challengeId, SubmitCheckInRequest form, MultipartFile media) {
        Challenge challenge = preconditions.getActiveChallengeForActiveMember(challengeId, userId);

        String memo = (form.memo() == null || form.memo().isBlank()) ? null : form.memo();

        LocalDate businessDate = businessClock.today();
        policy.validateCheckInDay(challenge, businessDate);
        policy.validateAllowedType(challenge, form.checkInType());

        int target = challenge.getDailyCheckInCount();
        int already = checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, userId, businessDate);
        if (already >= target) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        String mediaKey = mediaStore.store(media);
        int roundNo = already + 1;

        CheckIn checkIn = new CheckIn(
                challengeId, userId, roundNo, form.checkInType(), mediaKey, MediaType.IMAGE, memo, businessDate);
        try {
            checkInRepository.saveAndFlush(checkIn);
        } catch (DataIntegrityViolationException e) {
            log.info("uk_check_ins 위반(같은 회차 선점됨): challengeId={}, userId={}, roundNo={}", challengeId, userId, roundNo);
            deleteQuietly(mediaKey); // 방금 쓴 orphan을 best-effort로 정리
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        // 미디어는 이미 스토리지에 올라갔다. 아래 후처리에서 예외가 나면 @Transactional 이 CheckIn 행은 롤백하지만
        // 파일은 남으므로, uk_check_ins 위반 처리와 같이 best-effort 로 정리하고 되던진다.
        String sourceName =
                challengeGroupRepository.findNameById(challenge.getGroupId()).orElse("인증");
        int earnedUserPoints = policy.checkInReward();
        try {
            // 포인트 적립 — 같은 트랜잭션. 실패 시 인증도 롤백된다.
            // PersonalPointService.reward 는 전달한 sourceName 을 그대로 저장. 포인트 이력 화면이 그룹명을
            // 보여줘야 해서 챌린지가 속한 그룹명을 넘긴다. (그룹이 지워진 예외적 상황이면 고정 라벨로 폴백)
            personalPointService.reward(userId, challengeId, earnedUserPoints, UserPointReason.CHECK_IN, sourceName);

            String nickname = nicknameOf(userId, checkIn.getId());

            // 이번 인증이 그날 목표를 채운 마지막 회차면 개인/그룹/유저 스트릭을 갱신한다. 같은 트랜잭션.
            boolean dailyCompleted = roundNo >= target;
            int currentStreak = 0;
            int groupCompletedCount = 0;
            int groupTotalCount = 0;
            if (dailyCompleted) {
                MemberCheckInResult streak =
                        challengeStreakService.onMemberDailyComplete(challengeId, userId, businessDate);
                currentStreak = streak.memberCurrentStreak();
                groupCompletedCount = streak.groupCompletedCount();
                groupTotalCount = streak.groupTotalCount();
            }

            return new CheckInResultResponse(
                    CheckInResponse.of(checkIn, nickname),
                    roundNo,
                    target,
                    dailyCompleted,
                    earnedUserPoints,
                    currentStreak,
                    groupCompletedCount,
                    groupTotalCount);
        } catch (RuntimeException e) {
            log.error(
                    "인증 후처리 실패로 롤백: challengeId={}, userId={}, roundNo={}, earnedUserPoints={}",
                    challengeId,
                    userId,
                    roundNo,
                    earnedUserPoints,
                    e);
            deleteQuietly(mediaKey); // 롤백은 CheckIn 행만 지운다. 올라간 파일은 best-effort 로 정리
            throw e;
        }
    }

    public CheckInCursorResponse getGallery(
            Long userId,
            Long challengeId,
            YearMonth month,
            Long filterUserId,
            CheckInType checkInType,
            Long cursor,
            int size) {
        ReadDateAccess access = preconditions.resolveReadDateAccess(challengeId, userId);

        LocalDate from = (month == null) ? null : month.atDay(1);
        LocalDate to = (month == null) ? null : month.atEndOfMonth();

        List<CheckIn> rows = checkInRepository.findGallery(
                challengeId,
                from,
                to,
                filterUserId,
                checkInType,
                access.maxBusinessDate(),
                cursor,
                PageRequest.of(0, size + 1));

        SliceResponse<CheckIn> page = SliceResponse.ofCursor(rows, size, CheckIn::getId);
        Map<Long, String> nicknames = nicknamesOf(page.content());
        List<CheckInResponse> content = page.content().stream()
                .map(c -> CheckInResponse.of(c, nicknames.get(c.getUserId())))
                .toList();

        return new CheckInCursorResponse(content, new CursorPageMeta(page.nextCursor(), page.hasNext(), size));
    }

    // 최근 인증 로그 한줄보기
    public RecentCheckInResponse getRecent(Long userId, Long challengeId, int size) {
        ReadDateAccess access = preconditions.resolveReadDateAccess(challengeId, userId);

        List<CheckIn> rows = checkInRepository.findRecent(challengeId, access.maxBusinessDate(), Limit.of(size));
        Map<Long, String> nicknames = nicknamesOf(rows);
        String groupName = challengeGroupRepository
                .findNameById(access.challenge().getGroupId())
                .orElse("챌린지");

        List<RecentCheckInItem> items = rows.stream()
                .map(c -> {
                    String nickname = nicknames.get(c.getUserId());
                    return new RecentCheckInItem(
                            c.getId(),
                            nickname,
                            recentText(nickname, c.getMemo(), groupName),
                            null, // earnedUserPoints — 포인트 도메인(#7)
                            c.getCreatedAt());
                })
                .toList();

        return new RecentCheckInResponse(items);
    }

    // 한줄보기 문구 = "{이름}_{메모}". 메모 없는 인증은 메모 자리를 그룹명으로 채운다.
    private static String recentText(String nickname, String memo, String groupName) {
        String tail = (memo == null || memo.isBlank()) ? groupName : memo;
        return "%s_%s".formatted(nickname, tail);
    }

    public CheckInResponse getCheckIn(Long userId, Long challengeId, Long checkInId) {
        CheckIn checkIn = readableCheckIn(userId, challengeId, checkInId);
        return CheckInResponse.of(checkIn, nicknameOf(checkIn.getUserId(), checkIn.getId()));
    }

    public MyCheckInCursorResponse getMyCheckIns(
            Long userId, Long challengeId, CheckInType checkInType, YearMonth month, Long cursor, int size) {
        if (challengeId != null) {
            preconditions.getChallenge(challengeId); // 존재하지 않으면 404
        }

        LocalDate from = month == null ? null : month.atDay(1);
        LocalDate to = month == null ? null : month.atEndOfMonth();

        List<CheckIn> rows = checkInRepository.findMine(
                userId, challengeId, checkInType, from, to, cursor, PageRequest.of(0, size + 1));
        long totalCount = checkInRepository.countMine(userId, challengeId, checkInType, from, to);

        SliceResponse<CheckIn> page = SliceResponse.ofCursor(rows, size, CheckIn::getId);
        Map<Long, String> nicknames = nicknamesOf(page.content());
        List<MyCheckInResponse> content = page.content().stream()
                .map(c -> MyCheckInResponse.of(c, nicknames.get(c.getUserId())))
                .toList();

        return new MyCheckInCursorResponse(
                content, new MyCheckInPageMeta(page.nextCursor(), page.hasNext(), size, totalCount));
    }

    public Resource loadCheckInMedia(Long userId, Long checkInId) {
        CheckIn checkIn = checkInRepository
                .findById(checkInId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECK_IN_NOT_FOUND));
        ReadDateAccess access = preconditions.resolveReadDateAccess(checkIn.getChallengeId(), userId);
        if (!access.allows(checkIn.getBusinessDate())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        try {
            return mediaStore.load(checkIn.getMediaKey());
        } catch (BusinessException e) {
            log.warn(
                    "미디어 로드 실패: checkInId={}, mediaKey={}, code={}",
                    checkInId,
                    checkIn.getMediaKey(),
                    e.getErrorCode());
            throw e;
        }
    }

    private CheckIn readableCheckIn(Long userId, Long challengeId, Long checkInId) {
        CheckIn checkIn = checkInRepository
                .findById(checkInId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECK_IN_NOT_FOUND));
        if (!checkIn.getChallengeId().equals(challengeId)) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        ReadDateAccess access = preconditions.resolveReadDateAccess(challengeId, userId);
        if (!access.allows(checkIn.getBusinessDate())) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        return checkIn;
    }

    private void deleteQuietly(String storageKey) {
        try {
            mediaStore.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("orphan 미디어 정리 실패: {}", storageKey, ex);
        }
    }

    private String nicknameOf(Long userId, Long checkInId) {
        String nickname = userService.findNicknames(List.of(userId)).get(userId);
        if (nickname == null) {
            log.warn("닉네임 조회 실패(유저 없음 추정): userId={}, checkInId={}", userId, checkInId);
        }
        return nickname;
    }

    private Map<Long, String> nicknamesOf(List<CheckIn> rows) {
        List<Long> userIds = rows.stream().map(CheckIn::getUserId).distinct().toList();
        Map<Long, String> nicknames = userService.findNicknames(userIds);
        rows.stream()
                .filter(r -> !nicknames.containsKey(r.getUserId()))
                .forEach(r -> log.warn("닉네임 조회 실패(유저 없음 추정): userId={}, checkInId={}", r.getUserId(), r.getId()));
        return nicknames;
    }
}

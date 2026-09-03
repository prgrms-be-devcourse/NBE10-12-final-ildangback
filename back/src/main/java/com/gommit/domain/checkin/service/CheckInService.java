package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.dto.request.SubmitCheckInForm;
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
import com.gommit.domain.checkin.support.CheckInGuard;
import com.gommit.domain.checkin.support.CheckInGuard.ReadAccess;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final CheckInGuard guard;
    private final CheckInPolicy policy;
    private final CheckInMediaStore mediaStore;
    private final UserService userService;
    private final Clock clock;

    public TodayCheckInStatusResponse getTodayStatus(Long userId, Long challengeId) {
        Challenge challenge = guard.getChallengeForActiveMember(challengeId, userId);
        LocalDate today = LocalDate.now(clock);

        int target = challenge.getDailyCheckInCount();
        int current = checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, userId, today);

        return new TodayCheckInStatusResponse(
                today,
                policy.isCheckInDay(challenge, today),
                current,
                target,
                current >= target,
                policy.allowedTypes(challenge));
    }

    @Transactional
    public CheckInResultResponse submit(Long userId, Long challengeId, SubmitCheckInForm form, MultipartFile media) {
        Challenge challenge = guard.getChallenge(challengeId);
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_ACTIVE);
        }

        ChallengeMember member = challengeMemberRepository
                .findByChallenge_IdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));
        if (member.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        String memo = blankToNull(form.memo());
        if (memo != null && memo.length() > 100) {
            throw new BusinessException(ErrorCode.MEMO_TOO_LONG);
        }

        LocalDate businessDate = LocalDate.now(clock);
        if (!policy.isCheckInDay(challenge, businessDate)) {
            throw new BusinessException(ErrorCode.NOT_CHECK_IN_DAY);
        }
        if (!policy.allows(challenge, form.checkInType())) {
            throw new BusinessException(ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }

        int target = challenge.getDailyCheckInCount();
        int already = checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, userId, businessDate);
        if (already >= target) {
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        String mediaKey = mediaStore.reserve(media); // 검증 + 키 확보, 바이트는 아직 안 씀
        int roundNo = already + 1;

        CheckIn checkIn = CheckIn.create(
                challengeId, userId, roundNo, form.checkInType(), mediaKey, MediaType.IMAGE, memo, businessDate);
        try {
            checkInRepository.saveAndFlush(checkIn);
        } catch (DataIntegrityViolationException e) {
            // uk_check_ins 위반 = 같은 회차를 이미 다른 요청이 선점 → 한도 초과와 동일 취급. 파일은 아직 안 썼다.
            throw new BusinessException(ErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        String nickname = nicknameOf(userId);
        mediaStore.write(media, mediaKey); // row 확정 후에만 기록. 실패 시 트랜잭션 롤백으로 row 도 함께 사라진다.

        int earnedUserPoints = 0; // TODO(feat/7): pointService.reward(...) 로 실제 적립
        boolean dailyCompleted = roundNo >= target;
        return new CheckInResultResponse(
                CheckInResponse.of(checkIn, nickname), roundNo, target, dailyCompleted, earnedUserPoints, 0, 0, 0);
    }

    public CheckInCursorResponse getGallery(
            Long userId,
            Long challengeId,
            LocalDate date,
            Long filterUserId,
            CheckInType checkInType,
            Long cursor,
            int size) {
        ReadAccess access = guard.resolveReadAccess(challengeId, userId);

        List<CheckIn> rows = checkInRepository.findGallery(
                challengeId,
                date,
                filterUserId,
                checkInType,
                access.maxBusinessDate(),
                cursor,
                PageRequest.of(0, size + 1));

        CursorPage page = CursorPage.of(rows, size);
        Map<Long, String> nicknames = nicknamesOf(page.content());
        List<CheckInResponse> content = page.content().stream()
                .map(c -> CheckInResponse.of(c, nicknames.get(c.getUserId())))
                .toList();

        return new CheckInCursorResponse(content, new CursorPageMeta(page.nextCursor(), page.hasNext(), size));
    }

    // 최근 인증 로그 한줄보기
    public RecentCheckInResponse getRecent(Long userId, Long challengeId, int size) {
        ReadAccess access = guard.resolveReadAccess(challengeId, userId);

        List<CheckIn> rows = checkInRepository.findRecent(challengeId, access.maxBusinessDate(), Limit.of(size));
        Map<Long, String> nicknames = nicknamesOf(rows);

        List<RecentCheckInItem> items = rows.stream()
                .map(c -> {
                    String nickname = nicknames.get(c.getUserId());
                    return new RecentCheckInItem(
                            c.getId(),
                            nickname,
                            "%s님이 인증을 남겼어요".formatted(nickname),
                            null, // earnedUserPoints — 포인트 도메인(#7)
                            c.getCreatedAt());
                })
                .toList();

        return new RecentCheckInResponse(items);
    }

    // 인증 단건 조회
    public CheckInResponse getCheckIn(Long userId, Long challengeId, Long checkInId) {
        CheckIn checkIn = readableCheckIn(userId, challengeId, checkInId);
        return CheckInResponse.of(checkIn, nicknameOf(checkIn.getUserId()));
    }

    public MyCheckInCursorResponse getMyCheckIns(
            Long userId, Long challengeId, CheckInType checkInType, YearMonth month, Long cursor, int size) {
        if (challengeId != null) {
            guard.getChallenge(challengeId); // 존재하지 않으면 404
        }

        LocalDate from = month == null ? null : month.atDay(1);
        LocalDate to = month == null ? null : month.atEndOfMonth();

        List<CheckIn> rows = checkInRepository.findMine(
                userId, challengeId, checkInType, from, to, cursor, PageRequest.of(0, size + 1));
        long totalCount = checkInRepository.countMine(userId, challengeId, checkInType, from, to);

        CursorPage page = CursorPage.of(rows, size);
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
        ReadAccess access = guard.resolveReadAccess(checkIn.getChallengeId(), userId);
        if (!access.allows(checkIn.getBusinessDate())) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
        return mediaStore.load(checkIn.getMediaKey());
    }

    private CheckIn readableCheckIn(Long userId, Long challengeId, Long checkInId) {
        CheckIn checkIn = checkInRepository
                .findById(checkInId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHECK_IN_NOT_FOUND));
        if (!checkIn.getChallengeId().equals(challengeId)) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
        ReadAccess access = guard.resolveReadAccess(challengeId, userId);
        if (!access.allows(checkIn.getBusinessDate())) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }
        return checkIn;
    }

    private String nicknameOf(Long userId) {
        return userService.findNicknames(List.of(userId)).get(userId);
    }

    private Map<Long, String> nicknamesOf(List<CheckIn> rows) {
        List<Long> userIds = rows.stream().map(CheckIn::getUserId).distinct().toList();
        return userService.findNicknames(userIds);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private record CursorPage(List<CheckIn> content, boolean hasNext, Long nextCursor) {

        static CursorPage of(List<CheckIn> rows, int size) {
            boolean hasNext = rows.size() > size;
            List<CheckIn> content = hasNext ? rows.subList(0, size) : rows;
            Long nextCursor =
                    content.isEmpty() ? null : content.get(content.size() - 1).getId();
            return new CursorPage(content, hasNext, hasNext ? nextCursor : null);
        }
    }
}

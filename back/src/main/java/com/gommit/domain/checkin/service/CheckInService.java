package com.gommit.domain.checkin.service;

import com.gommit.domain.checkin.dto.request.SubmitCheckInRequest;
import com.gommit.domain.checkin.dto.response.CheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.CheckInResponse;
import com.gommit.domain.checkin.dto.response.CheckInResultResponse;
import com.gommit.domain.checkin.dto.response.MyCheckInCursorResponse;
import com.gommit.domain.checkin.dto.response.RecentCheckInResponse;
import com.gommit.domain.checkin.dto.response.TodayCheckInStatusResponse;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.media.CheckInMediaStore;
import com.gommit.domain.checkin.policy.CheckInPolicy;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.checkin.support.CheckInPreconditions;
import com.gommit.domain.point.service.PointService;
import com.gommit.domain.user.service.UserService;
import java.time.Clock;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// 스켈레톤 — 구현은 "feat: 인증 서비스 + 정책 + 접근 판정" 커밋에서 채운다.
@Service
@RequiredArgsConstructor
public class CheckInService {

    private final CheckInRepository checkInRepository;
    private final CheckInPreconditions preconditions;
    private final CheckInPolicy policy;
    private final CheckInMediaStore mediaStore;
    private final PointService pointService;
    private final UserService userService;
    private final Clock clock;

    public TodayCheckInStatusResponse getTodayStatus(Long userId, Long challengeId) {
        throw new UnsupportedOperationException("미구현");
    }

    public CheckInResultResponse submit(Long userId, Long challengeId, SubmitCheckInRequest form, MultipartFile media) {
        throw new UnsupportedOperationException("미구현");
    }

    public CheckInCursorResponse getGallery(
            Long userId,
            Long challengeId,
            YearMonth month,
            Long filterUserId,
            CheckInType checkInType,
            Long cursor,
            int size) {
        throw new UnsupportedOperationException("미구현");
    }

    public RecentCheckInResponse getRecent(Long userId, Long challengeId, int size) {
        throw new UnsupportedOperationException("미구현");
    }

    public CheckInResponse getCheckIn(Long userId, Long challengeId, Long checkInId) {
        throw new UnsupportedOperationException("미구현");
    }

    public MyCheckInCursorResponse getMyCheckIns(
            Long userId, Long challengeId, CheckInType checkInType, YearMonth month, Long cursor, int size) {
        throw new UnsupportedOperationException("미구현");
    }

    public Resource loadCheckInMedia(Long userId, Long checkInId) {
        throw new UnsupportedOperationException("미구현");
    }
}

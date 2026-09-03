package com.gommit.domain.checkin.service;

import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.checkin.dto.request.SubmitCheckInForm;
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
import com.gommit.domain.checkin.support.CheckInGuard;
import com.gommit.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

// TODO(commit): 6개 유스케이스 + 미디어 서빙 구현. 지금은 스켈레톤.
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
        throw new UnsupportedOperationException("not implemented");
    }

    public CheckInResultResponse submit(Long userId, Long challengeId, SubmitCheckInForm form, MultipartFile media) {
        throw new UnsupportedOperationException("not implemented");
    }

    public CheckInCursorResponse getGallery(
            Long userId,
            Long challengeId,
            LocalDate date,
            Long filterUserId,
            CheckInType checkInType,
            Long cursor,
            int size) {
        throw new UnsupportedOperationException("not implemented");
    }

    public RecentCheckInResponse getRecent(Long userId, Long challengeId, int size) {
        throw new UnsupportedOperationException("not implemented");
    }

    public CheckInResponse getCheckIn(Long userId, Long challengeId, Long checkInId) {
        throw new UnsupportedOperationException("not implemented");
    }

    public MyCheckInCursorResponse getMyCheckIns(
            Long userId, Long challengeId, CheckInType checkInType, YearMonth month, Long cursor, int size) {
        throw new UnsupportedOperationException("not implemented");
    }

    public Resource loadCheckInMedia(Long userId, Long checkInId) {
        throw new UnsupportedOperationException("not implemented");
    }
}

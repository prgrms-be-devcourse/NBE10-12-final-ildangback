package com.gommit.domain.checkin.dto.response;

import java.time.LocalDateTime;
import java.util.List;

// api-spec: CheckIn_RecentCheckInResponse
public record RecentCheckInResponse(List<RecentCheckInItem> items) {

    // api-spec: CheckIn_RecentCheckInItem
    // earnedUserPoints 는 포인트 도메인(#7) 연동 전까지 null.
    public record RecentCheckInItem(
            Long checkInId, String nickname, String text, Integer earnedUserPoints, LocalDateTime createdAt) {}
}

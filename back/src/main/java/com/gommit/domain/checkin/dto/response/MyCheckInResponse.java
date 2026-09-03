package com.gommit.domain.checkin.dto.response;

import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaUrl;
import java.time.LocalDate;
import java.time.LocalDateTime;

// api-spec: CheckIn_MyCheckIn. CheckIn_CheckIn 을 allOf 로 확장해 challengeId 추가.
public record MyCheckInResponse(
        Long id,
        Long userId,
        String nickname,
        LocalDate businessDate,
        int roundNo,
        CheckInType checkInType,
        String mediaUrl,
        MediaType mediaType,
        String memo,
        LocalDateTime createdAt,
        Long challengeId) {

    public static MyCheckInResponse of(CheckIn checkIn, String nickname) {
        return new MyCheckInResponse(
                checkIn.getId(),
                checkIn.getUserId(),
                nickname,
                checkIn.getBusinessDate(),
                checkIn.getRoundNo(),
                checkIn.getCheckInType(),
                CheckInMediaUrl.of(checkIn.getId()),
                checkIn.getMediaType(),
                checkIn.getMemo(),
                checkIn.getCreatedAt(),
                checkIn.getChallengeId());
    }
}

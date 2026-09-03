package com.gommit.domain.checkin.dto.response;

import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.media.CheckInMediaUrl;
import java.time.LocalDate;
import java.time.LocalDateTime;

// api-spec: CheckIn_CheckIn
public record CheckInResponse(
        Long id,
        Long userId,
        String nickname,
        LocalDate businessDate,
        int roundNo,
        CheckInType checkInType,
        String mediaUrl,
        MediaType mediaType,
        String memo,
        LocalDateTime createdAt) {

    public static CheckInResponse of(CheckIn checkIn, String nickname) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getUserId(),
                nickname,
                checkIn.getBusinessDate(),
                checkIn.getRoundNo(),
                checkIn.getCheckInType(),
                CheckInMediaUrl.of(checkIn.getId()),
                checkIn.getMediaType(),
                checkIn.getMemo(),
                checkIn.getCreatedAt());
    }
}

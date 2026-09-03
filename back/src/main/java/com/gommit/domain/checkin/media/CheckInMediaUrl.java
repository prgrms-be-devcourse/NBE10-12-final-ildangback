package com.gommit.domain.checkin.media;

// 인증 미디어 조회용 상대경로 URL. 프론트에서 origin(예: https://go-mmit.site)을 붙인다.
public final class CheckInMediaUrl {

    private CheckInMediaUrl() {}

    public static String of(Long checkInId) {
        return "/api/check-ins/" + checkInId + "/media";
    }
}

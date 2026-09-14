package com.gommit.domain.checkin.media;

// 인증 미디어 조회용 상대경로 URL. 프론트에서 origin(예: https://go-mmit.site)을 붙인다.
public final class CheckInMediaUrl {

    private CheckInMediaUrl() {}

    public static String of(Long checkInId) {
        return "/api/check-ins/" + checkInId + "/media";
    }

    // 영상 체크인의 그리드 표시용 썸네일 URL. 이미지 체크인엔 의미 없음(hasPoster 가 false 면 null).
    public static String posterOf(Long checkInId) {
        return "/api/check-ins/" + checkInId + "/media/poster";
    }

    // CheckInResponse.of/MyCheckInResponse.of 가 반복하던 posterKey null 체크를 여기로 모은다.
    public static String posterOfOrNull(Long checkInId, boolean hasPoster) {
        return hasPoster ? posterOf(checkInId) : null;
    }
}

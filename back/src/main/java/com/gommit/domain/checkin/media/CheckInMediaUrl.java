package com.gommit.domain.checkin.media;

// 인증 미디어 조회 URL. 동일 오리진 상대경로로 내려 프론트가 origin 을 붙인다.
// 스토리지 키는 노출하지 않고 checkInId 로 서빙한다 — 팀 결정(2026-09-03).
public final class CheckInMediaUrl {

    private CheckInMediaUrl() {}

    public static String of(Long checkInId) {
        return "/api/check-ins/" + checkInId + "/media";
    }
}

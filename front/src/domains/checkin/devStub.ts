import type { CheckInResultResponse, TodayCheckInStatus } from "./types";

/**
 * 임시 스텁 — 백엔드에 checkin 엔드포인트가 아직 없다(feat/13 브랜치 기준).
 *
 * 백엔드 스펙(front/docs/checkin-api-spec.yml)이 실제 구현되어 머지되면:
 *   1. 이 파일을 지운다
 *   2. api.ts 의 `isCheckInStubEnabled()` 분기와 import 를 지운다
 * 그러면 남는 건 `apiFetch` 호출뿐이다.
 */

export function isCheckInStubEnabled(): boolean {
  // dev 서버에서만. `VITE_CHECKIN_STUB=off` 로 끄고 실제 백엔드를 붙일 수도 있다.
  return import.meta.env.DEV && import.meta.env.VITE_CHECKIN_STUB !== "off";
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function stubTodayStatus(): TodayCheckInStatus {
  return {
    businessDate: today(),
    isCheckInDay: true,
    currentCount: 0,
    targetCount: 1,
    completed: false,
    allowedTypes: ["PHOTO"],
  };
}

export function stubSubmitResult(input: {
  memo?: string;
}): CheckInResultResponse {
  return {
    checkIn: {
      id: 1,
      userId: 1,
      nickname: "나",
      businessDate: today(),
      roundNo: 1,
      checkInType: "PHOTO",
      mediaUrl: "https://placehold.co/600x600/8058c4/fff?text=CHECK-IN",
      mediaType: "IMAGE",
      memo: input.memo ?? null,
      createdAt: new Date().toISOString(),
    },
    currentCount: 1,
    targetCount: 1,
    dailyCompleted: true,
    earnedUserPoints: 10,
    currentStreak: 12,
    groupCompletedCount: 4,
    groupTotalCount: 5,
  };
}

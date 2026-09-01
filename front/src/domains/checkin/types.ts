/**
 * front/docs/checkin-api-spec.yml 의 스키마를 옮긴 것이다. 그쪽이 정본이며,
 * 이 세션 스코프(제출 플로우)에 필요한 것만 담는다 — 갤러리 · 모아보기 · 일일로그는 제외.
 */

/** 현재 PHOTO 만 지원. VIDEO 는 스펙 enum 에 아직 없음. */
export type CheckInType = "PHOTO";

export type MediaType = "IMAGE" | "VIDEO";

export interface CheckIn {
  id: number;
  userId: number;
  nickname: string;
  businessDate: string;
  roundNo: number;
  checkInType: CheckInType;
  mediaUrl: string;
  mediaType: MediaType;
  memo: string | null;
  createdAt: string;
}

/** GET /challenges/{id}/check-ins/today */
export interface TodayCheckInStatus {
  businessDate: string;
  isCheckInDay: boolean;
  currentCount: number;
  targetCount: number;
  completed: boolean;
  allowedTypes: CheckInType[];
}

/** POST /challenges/{id}/check-ins 응답 (201) */
export interface CheckInResultResponse {
  checkIn: CheckIn;
  currentCount: number;
  targetCount: number;
  dailyCompleted: boolean;
  earnedUserPoints: number;
  currentStreak: number;
  groupCompletedCount: number;
  groupTotalCount: number;
}

/** 카메라에서 막 잡은 정사각 jpeg. previewUrl 은 URL.createObjectURL 결과. */
export interface CapturedPhoto {
  blob: Blob;
  previewUrl: string;
}

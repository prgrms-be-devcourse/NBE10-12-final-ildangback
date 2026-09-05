/**
 * front/docs/checkin-api-spec.yml 의 스키마를 옮긴 것이다. 그쪽이 정본이며,
 * 제출 플로우 + 갤러리 탭에 필요한 것만 담는다 — 프로필 모아보기 · 일일로그는 제외.
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

/** 커서 페이징 메타. `checkin-api-spec.yml` 의 CursorPageMeta. */
export interface CursorPageMeta {
  /** 다음 페이지 커서(마지막으로 받은 id). 없으면 null. */
  nextCursor: number | null;
  hasNext: boolean;
  size: number;
}

/** GET /challenges/{id}/check-ins (갤러리 - 무한스크롤) 응답. */
export interface CheckInCursorResponse {
  content: CheckIn[];
  meta: CursorPageMeta;
}

/**
 * 챌린지 참여자 한 명. 갤러리 참여자 필터 칩에 쓴다.
 *
 * challenge 도메인 몫이다. `feat/6-group-challenge`(#31)의
 * `GET /api/challenges/{id}/members` → `MemberTodayStatusResponse` 와 형태를 맞춘 것.
 * #31 이 머지되면 이 타입·`getChallengeMembers` 를 challenge 도메인으로 옮긴다.
 * (front/docs/checkin-gallery-backend-asks.md 참고)
 */
export interface ChallengeMember {
  userId: number;
  nickname: string;
  /** 오늘 인증 횟수. 현황 탭용 — 갤러리 칩에선 안 쓴다. */
  todayCheckInCount: number;
}

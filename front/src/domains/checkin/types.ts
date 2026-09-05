/**
 * front/docs/checkin-api-spec.yml 의 스키마를 옮긴 것이다. 그쪽이 정본이며,
 * 제출 플로우 · 갤러리 탭 · 프로필 모아보기 · 일일 로그에 필요한 것을 담는다.
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

// ── 프로필 > 내 인증 모아보기 (GET /users/me/check-ins) ──────────────────────

/** `CheckIn` + 상세 경로 조립용 `challengeId`. 여러 챌린지가 섞여 온다. */
export interface MyCheckIn extends CheckIn {
  challengeId: number;
}

export interface MyCheckInPageMeta extends CursorPageMeta {
  /**
   * 본인 인증 총 횟수. `challengeId`·`checkInType` 필터는 반영하되 `month` 는 무시 —
   * 월을 바꿔도 헤더 수치가 안 흔들린다.
   */
  totalCount: number;
}

export interface MyCheckInCursorResponse {
  content: MyCheckIn[];
  meta: MyCheckInPageMeta;
}

/**
 * 내가 참여한(했던) 챌린지 한 건. "전체 인증" 화면의 챌린지 드롭다운용.
 *
 * ⚠️ challenge/group 도메인 몫(`GET /groups/me` 등). 아직 프론트에 그 도메인이 없어
 * 임시로 둔다. (front/docs/checkin-gallery-backend-asks.md)
 */
export interface MyChallengeSummary {
  challengeId: number;
  /** 그룹명 (챌린지 자체엔 이름이 없음) */
  name: string;
}

/**
 * 그룹 앨범 화면 상단 헤더용. 실제로는 `GET /challenges/{id}`(#31, 기간·상태) +
 * `GET /groups/{groupId}`(#31, 이름·카테고리) 두 응답을 합쳐야 나온다.
 * (front/docs/checkin-gallery-backend-asks.md)
 */
export interface ChallengeAlbumSummary {
  challengeId: number;
  /** 그룹명 */
  name: string;
  /** 그룹 카테고리 ("운동" 등) */
  category: string;
  /** 진행 중이면 true (ChallengeStatus === IN_PROGRESS) */
  active: boolean;
  startDate: string;
  endDate: string;
}

// ── 일일 로그 (GET /challenges/{id}/daily-logs) ─────────────────────────────

/**
 * 하루치 일일 로그. 서버가 ffmpeg 로 참여자 인증을 타일로 합쳐 만든 영상 1개다.
 * `videoUrl` 은 영상 생성 전이면 null — 그동안 프론트는 타일 자리를 placeholder 로 채운다.
 */
export interface DailyLog {
  id: number;
  businessDate: string;
  videoUrl: string | null;
  /** 당일 목표 달성 인원 */
  completedCount: number;
  /** 전체 그룹 인원 */
  totalCount: number;
}

/**
 * 커서 메타 + 헤더 배너용 월 집계.
 *
 * ⚠️ `recordDays`·`avgRate` 는 스펙에 아직 없음 — daily-log API 는 후속 PR 이며 그때
 * `GET /challenges/{id}/daily-logs` 에 `month` 파라미터와 이 집계 메타를 함께 요청한다.
 * (front/docs/checkin-gallery-backend-asks.md)
 */
export interface DailyLogPageMeta extends CursorPageMeta {
  /** 이번 달 기록이 있는 날 수 */
  recordDays: number;
  /** 이번 달 평균 달성률 (0~100) */
  avgRate: number;
}

export interface DailyLogCursorResponse {
  content: DailyLog[];
  meta: DailyLogPageMeta;
}

/** checkin 도메인 응답/요청 타입. 제출 플로우 · 갤러리 탭 · 프로필 모아보기 · 일일 로그에서 쓴다. */

/** 프론트는 현재 PHOTO 촬영만 지원. 백엔드 enum 엔 VIDEO·LIVE 도 있음(UI 미구현). */
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

/**
 * 커서 페이징 메타 (프론트 내부 모델).
 *
 * 갤러리·내 인증 응답은 서버가 공통 SliceResponse(평면: content/hasNext/nextCursor)를 주고
 * api.ts 의 toNestedPage 어댑터가 이 중첩 형태로 되감싼다. `size` 는 서버 응답엔 없고
 * 어댑터가 요청값으로 채운다. 일일 로그는 실서버(daily-logs PR)도 같은 평면
 * SliceResponse 라 api.ts 의 전용 어댑터 `toDailyLogPage` 가 recordDays·avgRate 까지 계산해
 * 되감싼다(generic `toNestedPage` 로는 안 됨 — 그 두 필드는 서버에 없어 직접 내야 해서).
 */
export interface CursorPageMeta {
  /** 다음 페이지 커서(마지막으로 받은 id). 없으면 null. */
  nextCursor: number | null;
  hasNext: boolean;
  size: number;
}

/**
 * GET /challenges/{id}/check-ins (갤러리 - 무한스크롤) 응답 (프론트 내부 모델).
 * 서버는 SliceResponse(평면)를 주고 api.ts 가 이 형태로 변환한다.
 */
export interface CheckInCursorResponse {
  content: CheckIn[];
  meta: CursorPageMeta;
}

/**
 * 챌린지 참여자 한 명. 갤러리 참여자 필터 칩에 쓴다.
 *
 * challenge 도메인 몫. 프론트에 그 도메인이 생기면 이 타입을 그쪽으로 옮긴다.
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

/**
 * GET /users/me/check-ins 응답 (프론트 내부 모델).
 * 서버는 SliceResponse 평면 형태(content/hasNext/nextCursor/totalCount)를 주고
 * api.ts 의 toNestedPage 어댑터가 `totalCount` 를 meta 안으로 넣어 이 형태로 변환한다.
 */
export interface MyCheckInCursorResponse {
  content: MyCheckIn[];
  meta: MyCheckInPageMeta;
}

/**
 * 내가 참여한(했던) 챌린지 한 건. "전체 인증" 드롭다운 + "참여했던 챌린지" 앨범 카드용.
 *
 * ⚠️ challenge/group 도메인 몫(`GET /groups/me` 등). 아직 프론트에 그 도메인이 없어 임시로 둔다.
 */
export interface MyChallengeSummary {
  challengeId: number;
  /** 그룹명 (챌린지 자체엔 이름이 없음) */
  name: string;
}

/**
 * 그룹 앨범 화면 상단 헤더용. 실제로는 `GET /challenges/{id}`(기간·상태) +
 * `GET /groups/{groupId}`(이름·카테고리) 두 응답을 합쳐야 나온다.
 */
export interface ChallengeAlbumSummary {
  challengeId: number;
  /** 그룹명 */
  name: string;
  /** 그룹 카테고리 ("운동" 등) */
  category: string;
  /** 진행 중이면 true (ChallengeStatus === ACTIVE) */
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
 * `recordDays`·`avgRate` 는 서버가 안 주고 프론트가 계산한다 — `month` 필터로 그 달 row
 * 전체를 받아 각 row 의 `completedCount`/`totalCount` 로 직접 낸다.
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

/** 챌린지 현황의 "인증 로그" 한 줄. text 는 "닉네임_메모" 형식으로 서버가 만든다. */
export interface RecentCheckIn {
  checkInId: number;
  nickname: string;
  text: string;
  earnedUserPoints: number | null;
  /** LocalDateTime 문자열. */
  createdAt: string;
}

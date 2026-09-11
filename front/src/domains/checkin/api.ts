import { apiFetch } from "../../shared/api/client";
import {
  isCheckInStubEnabled,
  stubChallengeAlbumSummary,
  stubChallengeMembers,
  stubDailyLogs,
  stubGallery,
  stubMyChallenges,
  stubMyCheckIns,
  stubSubmitResult,
  stubTodayStatus,
} from "./devStub";
import type {
  ChallengeAlbumSummary,
  ChallengeMember,
  CheckIn,
  CheckInCursorResponse,
  CheckInResultResponse,
  CheckInType,
  CursorPageMeta,
  DailyLog,
  DailyLogCursorResponse,
  MyChallengeSummary,
  MyCheckIn,
  MyCheckInCursorResponse,
  TodayCheckInStatus,
} from "./types";

/**
 * 백엔드 SliceResponse(평면: content/hasNext/nextCursor[/totalCount])를
 * 프론트 내부 커서 페이지 모델({ content, meta })로 되감싼다.
 * useCursorPage 가 중첩 meta 를 기대하고, 일일 로그 스텁도 아직 중첩이라 경계에서만 변환.
 * 백엔드 근거: feat/20-checkin-mvp "인증 목록 응답을 공통 SliceResponse 로 통일"
 */
interface FlatSlice<T> {
  content: T[];
  hasNext: boolean;
  nextCursor: number | null;
  totalCount?: number;
}

function toNestedPage<T, Extra extends object = object>(
  flat: FlatSlice<T> & Extra,
  size: number,
): { content: T[]; meta: CursorPageMeta & Extra } {
  const { content, hasNext, nextCursor, ...extra } = flat;
  return {
    content,
    meta: { nextCursor, hasNext, size, ...(extra as Extra) },
  };
}

// ── challenge / group 도메인 실서버 응답 어댑터 ─────────────────────────────
// checkin 화면이 필요로 하는 최소 필드만 challenge/group API 응답에서 뽑아 쓴다.
// 이 도메인들의 프론트가 생기면 그쪽 api 로 옮기고 여기 어댑터는 지운다.

/** GET /api/groups/me — 공통 SliceResponse<MyGroupSummaryResponse> */
interface MyGroupSummaryResponse {
  groupId: number;
  /** 챌린지 멤버십 단위 응답이라 실질적으로 항상 채워진다. null 은 방어용. */
  challengeId: number | null;
  name: string;
}

/** GET /api/challenges/{id} — ChallengeStatusResponse */
interface ChallengeStatusResponse {
  challenge: {
    id: number;
    groupId: number;
    startDate: string;
    endDate: string;
    status: string;
  };
}

/** GET /api/groups/{id} — GroupDetailResponse */
interface GroupDetailResponse {
  group: { id: number; name: string; category: string };
}

/** GroupCategory enum → 화면 표시용 한글 라벨. */
const GROUP_CATEGORY_LABEL: Record<string, string> = {
  DEV: "개발",
  READING: "독서",
  JOB: "취업",
  STUDY: "공부",
  EXERCISE: "운동",
  HEALTH: "건강",
  LIFE: "생활습관",
  ETC: "기타",
};

/** 정의된 값만 쿼리스트링으로. `?a=1&b=2` 또는 빈 문자열. */
function buildQuery(
  params: Record<string, string | number | undefined>,
): string {
  const usp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v != null) usp.set(k, String(v));
  }
  const qs = usp.toString();
  return qs ? `?${qs}` : "";
}

/** GET /challenges/{challengeId}/check-ins/today */
export function getTodayCheckInStatus(
  challengeId: number,
): Promise<TodayCheckInStatus> {
  if (isCheckInStubEnabled()) return Promise.resolve(stubTodayStatus());
  return apiFetch(`/api/challenges/${challengeId}/check-ins/today`);
}

export interface SubmitCheckInInput {
  checkInType: CheckInType;
  /** 카메라에서 만든 정사각 jpeg */
  media: Blob;
  memo?: string;
}

/** POST /challenges/{challengeId}/check-ins (multipart/form-data) */
export function submitCheckIn(
  challengeId: number,
  input: SubmitCheckInInput,
): Promise<CheckInResultResponse> {
  if (isCheckInStubEnabled()) {
    return new Promise((resolve) =>
      setTimeout(() => resolve(stubSubmitResult({ memo: input.memo })), 400),
    );
  }

  const form = new FormData();
  form.append("checkInType", input.checkInType);
  form.append("media", input.media, "check-in.jpg");
  if (input.memo) form.append("memo", input.memo);

  return apiFetch(`/api/challenges/${challengeId}/check-ins`, {
    method: "POST",
    body: form,
  });
}

export interface GalleryQuery {
  /**
   * 월 필터 (yyyy-MM, `business_date` 기준). #28(feat/20-checkin-mvp)의
   * `getGallery` 에 정식 파라미터로 들어갔다(`date` 단일일 파라미터는 제거됨).
   * `/users/me/check-ins` 와 동일 패턴.
   */
  month?: string;
  /** 참여자 필터. 생략 시 전체 멤버. */
  userId?: number;
  /** 이전 응답 meta.nextCursor. 첫 페이지는 생략. */
  cursor?: number;
  size?: number;
}

/** GET /challenges/{challengeId}/check-ins (갤러리 - 무한스크롤) */
export function getChallengeGallery(
  challengeId: number,
  query: GalleryQuery = {},
): Promise<CheckInCursorResponse> {
  if (isCheckInStubEnabled()) {
    return new Promise((resolve) =>
      setTimeout(() => resolve(stubGallery(query)), 300),
    );
  }

  const size = query.size ?? 20;
  const qs = buildQuery({
    month: query.month,
    userId: query.userId,
    cursor: query.cursor,
    size,
  });
  return apiFetch<FlatSlice<CheckIn>>(
    `/api/challenges/${challengeId}/check-ins${qs}`,
  ).then((flat) => toNestedPage(flat, size));
}

/**
 * GET /challenges/{challengeId}/members ("시즌 멤버 오늘 인증 현황")
 *
 * challenge 도메인 몫. 프론트에 challenge 도메인이 생기면 이 함수를 그쪽 api 로 옮긴다.
 */
export function getChallengeMembers(
  challengeId: number,
): Promise<ChallengeMember[]> {
  if (isCheckInStubEnabled()) return Promise.resolve(stubChallengeMembers());
  return apiFetch(`/api/challenges/${challengeId}/members`);
}

export interface MyCheckInQuery {
  /** 지정 시 해당 챌린지의 본인 인증만 (그룹 앨범). 생략 시 전체 챌린지. */
  challengeId?: number;
  checkInType?: CheckInType;
  /** yyyy-MM */
  month?: string;
  cursor?: number;
  size?: number;
}

/** GET /users/me/check-ins (프로필 전체 인증 / 그룹 앨범 - 무한스크롤) */
export function getMyCheckIns(
  query: MyCheckInQuery = {},
): Promise<MyCheckInCursorResponse> {
  if (isCheckInStubEnabled()) {
    return new Promise((resolve) =>
      setTimeout(() => resolve(stubMyCheckIns(query)), 300),
    );
  }
  const size = query.size ?? 20;
  const qs = buildQuery({
    challengeId: query.challengeId,
    checkInType: query.checkInType,
    month: query.month,
    cursor: query.cursor,
    size,
  });
  return apiFetch<FlatSlice<MyCheckIn> & { totalCount: number }>(
    `/api/users/me/check-ins${qs}`,
  ).then((flat) => toNestedPage(flat, size));
}

/**
 * 내가 참여한(했던) 챌린지 목록. "전체 인증" 화면 챌린지 드롭다운 · "참여했던 챌린지" 그리드용.
 *
 * 실서버: `GET /api/groups/me` 는 SliceResponse<MyGroupSummaryResponse> 를 준다.
 * 응답은 챌린지 멤버십 단위라 challengeId 는 사실상 항상 채워진다(위 인터페이스 주석 참고).
 * null 필터는 스키마 변경에 대비한 방어 코드로 남겨둔다.
 * 챌린지 앨범 미리보기 커버 이미지(recentMediaUrls)는 아직 백엔드에 없음.
 */
export function getMyChallenges(): Promise<MyChallengeSummary[]> {
  if (isCheckInStubEnabled()) return Promise.resolve(stubMyChallenges());
  return apiFetch<FlatSlice<MyGroupSummaryResponse>>(`/api/groups/me`).then(
    (slice) =>
      slice.content
        .filter(
          (g): g is MyGroupSummaryResponse & { challengeId: number } =>
            g.challengeId != null,
        )
        .map((g) => ({ challengeId: g.challengeId, name: g.name })),
  );
}

/**
 * 그룹 앨범 헤더용 챌린지 요약.
 *
 * 실서버: 챌린지엔 이름·카테고리가 없어 `GET /api/challenges/{id}`(기간·상태·groupId) +
 * `GET /api/groups/{groupId}`(이름·카테고리) 두 호출을 합친다.
 */
export function getChallengeAlbumSummary(
  challengeId: number,
): Promise<ChallengeAlbumSummary> {
  if (isCheckInStubEnabled()) {
    return Promise.resolve(stubChallengeAlbumSummary(challengeId));
  }
  return apiFetch<ChallengeStatusResponse>(
    `/api/challenges/${challengeId}`,
  ).then(async ({ challenge }) => {
    const { group } = await apiFetch<GroupDetailResponse>(
      `/api/groups/${challenge.groupId}`,
    );
    return {
      challengeId: challenge.id,
      name: group.name,
      category: GROUP_CATEGORY_LABEL[group.category] ?? group.category,
      active: challenge.status === "ACTIVE",
      startDate: challenge.startDate,
      endDate: challenge.endDate,
    };
  });
}

/**
 * daily-logs 는 recordDays·avgRate 를 안 주니 content 로 직접 낸다. 그 달 row 는 최대
 * 31개라 size 를 그 이상으로 잡으면 한 페이지에 다 들어와 정확하다.
 */
function toDailyLogPage(
  flat: FlatSlice<DailyLog>,
  size: number,
): DailyLogCursorResponse {
  const { content, hasNext, nextCursor } = flat;
  const recordDays = content.length;
  const avgRate =
    recordDays === 0
      ? 0
      : Math.round(
          content.reduce(
            (sum, d) => sum + (d.completedCount / d.totalCount) * 100,
            0,
          ) / recordDays,
        );
  return {
    content,
    meta: { nextCursor, hasNext, size, recordDays, avgRate },
  };
}

export interface DailyLogQuery {
  /** yyyy-MM. ⚠️ 낙관적 — daily-logs PR(feat/36)엔 아직 cursor·size 만 있고 month 는 없음. */
  month?: string;
  cursor?: number;
  size?: number;
}

/** GET /challenges/{challengeId}/daily-logs (일일 로그 탭 - 무한스크롤) */
export function getDailyLogs(
  challengeId: number,
  query: DailyLogQuery = {},
): Promise<DailyLogCursorResponse> {
  if (isCheckInStubEnabled()) {
    return new Promise((resolve) =>
      setTimeout(() => resolve(stubDailyLogs(query)), 300),
    );
  }
  const size = query.size ?? 31;
  const qs = buildQuery({
    month: query.month,
    cursor: query.cursor,
    size,
  });
  return apiFetch<FlatSlice<DailyLog>>(
    `/api/challenges/${challengeId}/daily-logs${qs}`,
  ).then((flat) => toDailyLogPage(flat, size));
}

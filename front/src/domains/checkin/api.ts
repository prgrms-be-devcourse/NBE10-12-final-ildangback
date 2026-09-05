import { apiFetch } from "../../shared/api/client";
import {
  isCheckInStubEnabled,
  stubChallengeMembers,
  stubGallery,
  stubSubmitResult,
  stubTodayStatus,
} from "./devStub";
import type {
  ChallengeMember,
  CheckInCursorResponse,
  CheckInResultResponse,
  CheckInType,
  TodayCheckInStatus,
} from "./types";

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
   * 월 필터 (yyyy-MM). ⚠️ 낙관적 파라미터 — `checkin-api-spec.yml` 의
   * `GET /challenges/{id}/check-ins` 에는 아직 `month` 가 없다(`date` 단일일만).
   * `/users/me/check-ins` 와 동일 패턴으로 추가 요청 중.
   * (front/docs/checkin-gallery-backend-asks.md)
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

  const params = new URLSearchParams();
  if (query.month) params.set("month", query.month);
  if (query.userId != null) params.set("userId", String(query.userId));
  if (query.cursor != null) params.set("cursor", String(query.cursor));
  if (query.size != null) params.set("size", String(query.size));

  const qs = params.toString();
  return apiFetch(
    `/api/challenges/${challengeId}/check-ins${qs ? `?${qs}` : ""}`,
  );
}

/**
 * GET /challenges/{challengeId}/members ("시즌 멤버 오늘 인증 현황")
 *
 * challenge 도메인 몫. `feat/6-group-challenge`(#31)에 이 경로가 이미 있고
 * `MemberTodayStatusResponse[]` 를 준다 — `ChallengeMember` 는 그 형태에 맞췄다.
 * #31 이 머지되기 전까지만 dev 스텁으로 응답한다. 머지되면 스텁 분기를 지우고
 * 이 함수를 challenge 도메인 api 로 옮긴다.
 * (front/docs/checkin-gallery-backend-asks.md)
 */
export function getChallengeMembers(
  challengeId: number,
): Promise<ChallengeMember[]> {
  if (isCheckInStubEnabled()) return Promise.resolve(stubChallengeMembers());
  return apiFetch(`/api/challenges/${challengeId}/members`);
}

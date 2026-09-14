import type { CheckInType } from "../types";

/**
 * 인증 방법(사진/영상)을 체크인 페이지 라우트의 `?type=video` 쿼리로 인코딩/디코딩한다.
 * PHOTO 는 쿼리 없이 기본값 — 기존 딥링크·북마크와 호환.
 */
const VIDEO_PARAM = "video";

export function toCheckInTypeQuery(type: CheckInType): string {
  return type === "VIDEO" ? `?type=${VIDEO_PARAM}` : "";
}

export function parseCheckInTypeParam(
  searchParams: URLSearchParams,
): CheckInType {
  return searchParams.get("type") === VIDEO_PARAM ? "VIDEO" : "PHOTO";
}

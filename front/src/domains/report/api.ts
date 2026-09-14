import { apiFetch } from "../../shared/api/client";
import type { SliceResponse } from "../../shared/api/types";
import type {
  AppealDetailResponse,
  AppealResponse,
  AppealStatus,
  DecideAppealRequest,
  DecideReportRequest,
  DirectPenaltyRequest,
  MyPenaltyResponse,
  ReportDetailResponse,
  ReportResponse,
  ReportStatus,
  SubmitAppealRequest,
  SubmitReportRequest,
} from "./types";

// 컨트롤러가 @Max(100) 으로 막는 한 번 최대치다.
const PAGE_SIZE = 100;

/** POST /api/reports */
export function submitReport(
  request: SubmitReportRequest,
): Promise<ReportResponse> {
  return apiFetch("/api/reports", {
    method: "POST",
    body: request,
  });
}

/** POST /api/reports/{reportId}/appeals */
export function submitAppeal(
  reportId: number,
  request: SubmitAppealRequest,
): Promise<AppealResponse> {
  return apiFetch(`/api/reports/${reportId}/appeals`, {
    method: "POST",
    body: request,
  });
}

/** GET /api/penalties/me. 이의제기를 내려면 reportId 가 필요해서 화면이 이것부터 부른다. */
export function getMyPenalties(
  params: { cursor?: number | null; size?: number } = {},
): Promise<SliceResponse<MyPenaltyResponse>> {
  const query = new URLSearchParams();
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));
  const queryString = query.toString();
  return apiFetch(`/api/penalties/me${queryString ? `?${queryString}` : ""}`);
}

interface PageParams {
  cursor?: number | null;
  size?: number;
}

function pageQuery(status: string | null, params: PageParams): string {
  const query = new URLSearchParams();
  if (status) query.set("status", status);
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
}

/** GET /api/admin/reports (관리자) */
export function getReports(
  status: ReportStatus | null = null,
  params: PageParams = {},
): Promise<SliceResponse<ReportDetailResponse>> {
  return apiFetch(`/api/admin/reports${pageQuery(status, params)}`);
}

/** PATCH /api/admin/reports/{reportId} (관리자) */
export function decideReport(
  reportId: number,
  request: DecideReportRequest,
): Promise<ReportDetailResponse> {
  return apiFetch(`/api/admin/reports/${reportId}`, {
    method: "PATCH",
    body: request,
  });
}

/** GET /api/admin/appeals (관리자) */
export function getAppeals(
  status: AppealStatus | null = null,
  params: PageParams = {},
): Promise<SliceResponse<AppealDetailResponse>> {
  return apiFetch(`/api/admin/appeals${pageQuery(status, params)}`);
}

/** PATCH /api/admin/appeals/{appealId} (관리자) */
export function decideAppeal(
  appealId: number,
  request: DecideAppealRequest,
): Promise<AppealDetailResponse> {
  return apiFetch(`/api/admin/appeals/${appealId}`, {
    method: "PATCH",
    body: request,
  });
}

/** POST /api/admin/penalties (관리자). 신고 없이 거는 제재다. */
export function penalizeDirectly(
  request: DirectPenaltyRequest,
): Promise<ReportDetailResponse> {
  return apiFetch("/api/admin/penalties", {
    method: "POST",
    body: request,
  });
}

/**
 * 커서 페이징을 끝까지 따라가서 한 배열로 만든다. 아이템 상점과 같은 방식이다.
 *
 * 커서가 안 늘어나면 멈춘다 — 그 약속이 깨지면 같은 페이지를 끝없이 받는다.
 */
async function fetchAllPages<T>(
  load: (cursor: number | null) => Promise<SliceResponse<T>>,
): Promise<T[]> {
  const all: T[] = [];
  let cursor: number | null = null;

  for (;;) {
    const page = await load(cursor);
    all.push(...page.content);

    const next = page.hasNext ? page.nextCursor : null;
    if (next === null || (cursor !== null && next <= cursor)) return all;
    cursor = next;
  }
}

/** 내 제재 전체. 건수가 많을 리 없지만 커서를 끝까지 따라간다. */
export function getAllMyPenalties(): Promise<MyPenaltyResponse[]> {
  return fetchAllPages((cursor) => getMyPenalties({ cursor, size: PAGE_SIZE }));
}

/** 관리자 목록용 신고 전체. */
export function getAllReports(
  status: ReportStatus | null = null,
): Promise<ReportDetailResponse[]> {
  return fetchAllPages((cursor) =>
    getReports(status, { cursor, size: PAGE_SIZE }),
  );
}

/** 관리자 목록용 이의제기 전체. */
export function getAllAppeals(
  status: AppealStatus | null = null,
): Promise<AppealDetailResponse[]> {
  return fetchAllPages((cursor) =>
    getAppeals(status, { cursor, size: PAGE_SIZE }),
  );
}

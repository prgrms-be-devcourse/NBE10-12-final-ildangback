import { apiFetch } from "../../shared/api/client";
import type {
  PeriodFilter,
  PointBalanceResponse,
  PointChangeType,
  SliceResponse,
  UserPointHistoryResponse,
  UserPointReason,
} from "../../shared/api/types";

export interface GetMyHistoriesParams {
  period?: PeriodFilter;
  type?: PointChangeType;
  reason?: UserPointReason;
  /** 직접설정 시작일(YYYY-MM-DD). 있으면 서버가 period 대신 이 범위를 쓴다. */
  from?: string;
  /** 직접설정 종료일(YYYY-MM-DD, 포함). */
  to?: string;
  cursor?: number | null;
  size?: number;
}

export function getMyBalance(): Promise<PointBalanceResponse> {
  return apiFetch("/api/users/me/points");
}

export function getMyHistories(
  params: GetMyHistoriesParams = {},
): Promise<SliceResponse<UserPointHistoryResponse>> {
  const query = new URLSearchParams();
  if (params.period && params.period !== "ALL" && params.period !== "CUSTOM") {
    query.set("period", params.period);
  }
  if (params.type && params.type !== "ALL") {
    query.set("type", params.type);
  }
  if (params.reason) query.set("reason", params.reason);
  if (params.from) query.set("from", params.from);
  if (params.to) query.set("to", params.to);
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return apiFetch(
    `/api/users/me/points/histories${queryString ? `?${queryString}` : ""}`,
  );
}

export function getMyHistoryDetail(
  historyId: number,
): Promise<UserPointHistoryResponse> {
  return apiFetch(`/api/users/me/points/histories/${historyId}`);
}

import { apiFetch } from "../../shared/api/client";
import type {
  ChallengeMergeOverviewResponse,
  FinalMergeDetailResponse,
  MergeSummaryResponse,
  MonthlyMergeDetailResponse,
  MyMonthlyMergeResponse,
  PersonalStatsResponse,
  SliceResponse,
} from "../../shared/api/types";

export function getMyStats(): Promise<PersonalStatsResponse> {
  return apiFetch("/api/users/me/stats");
}

export interface GetMyChallengeMergeOverviewsParams {
  keyword?: string;
  category?: string;
  cursor?: number | null;
  size?: number;
}

export function getMyChallengeMergeOverviews(
  params: GetMyChallengeMergeOverviewsParams = {},
): Promise<SliceResponse<ChallengeMergeOverviewResponse>> {
  const query = new URLSearchParams();
  if (params.keyword) query.set("keyword", params.keyword);
  if (params.category) query.set("category", params.category);
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return apiFetch(
    `/api/users/me/challenge-merge-overviews${queryString ? `?${queryString}` : ""}`,
  );
}

export function getChallengeMergeOverview(
  challengeId: number,
): Promise<ChallengeMergeOverviewResponse> {
  return apiFetch(`/api/challenges/${challengeId}/merge-overview`);
}

export interface GetMergeListParams {
  cursor?: number | null;
  size?: number;
}

export function getMergeList(
  challengeId: number,
  params: GetMergeListParams = {},
): Promise<SliceResponse<MergeSummaryResponse>> {
  const query = new URLSearchParams();
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return apiFetch(
    `/api/challenges/${challengeId}/merges${queryString ? `?${queryString}` : ""}`,
  );
}

export function getMonthlyMergeDetail(
  challengeId: number,
  seqNo: number,
): Promise<MonthlyMergeDetailResponse> {
  return apiFetch(`/api/challenges/${challengeId}/monthly-merges/${seqNo}`);
}

export function getFinalMergeDetail(
  challengeId: number,
): Promise<FinalMergeDetailResponse> {
  return apiFetch(`/api/challenges/${challengeId}/final-merge`);
}

export interface GetMyMonthlyMergeArchiveParams {
  cursor?: number | null;
  size?: number;
}

export function getMyMonthlyMergeArchive(
  params: GetMyMonthlyMergeArchiveParams = {},
): Promise<SliceResponse<MyMonthlyMergeResponse>> {
  const query = new URLSearchParams();
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return apiFetch(
    `/api/users/me/monthly-merges${queryString ? `?${queryString}` : ""}`,
  );
}

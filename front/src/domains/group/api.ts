import { apiFetch } from "../../shared/api/client";
import type {
  GroupCreateRequest,
  GroupDetailResponse,
  GroupJoinResponse,
  GroupJoinRequest,
  InviteCodeResponse,
  PublicGroupQuery,
  MyGroupQuery,
  GroupSummary,
  MyGroupSummary,
  SliceResponse,
} from "./types";

export function getPublicGroups(query: PublicGroupQuery, cursor?: number) {
  const params = new URLSearchParams({
    sort: query.sort,
    size: String(query.size ?? 20),
  });
  if (query.keyword) params.set("keyword", query.keyword);
  if (query.category) params.set("category", query.category);
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return apiFetch<SliceResponse<GroupSummary>>(`/api/groups?${params}`);
}
export function getMyGroups(cursor?: number, query: MyGroupQuery = {}) {
  const params = new URLSearchParams({ size: String(query.size ?? 20) });
  if (query.status) params.set("status", query.status);
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return apiFetch<SliceResponse<MyGroupSummary>>(`/api/groups/me?${params}`);
}
export function getGroup(id: number) {
  return apiFetch<GroupDetailResponse>(`/api/groups/${id}`);
}
export function joinPublicGroup(id: number) {
  return apiFetch<GroupJoinResponse>(`/api/groups/${id}/members`, {
    method: "POST",
  });
}
export function createGroup(body: GroupCreateRequest) {
  return apiFetch<GroupDetailResponse>("/api/groups", { method: "POST", body });
}
export function joinByCode(body: GroupJoinRequest) {
  return apiFetch<GroupJoinResponse>("/api/groups/join", {
    method: "POST",
    body,
  });
}
export function getInviteCode(id: number) {
  return apiFetch<InviteCodeResponse>(`/api/groups/${id}/inviteCode`);
}
export function leaveGroup(id: number) {
  return apiFetch<void>(`/api/groups/${id}/members/me`, { method: "DELETE" });
}
export function kickGroupMember(id: number, userId: number) {
  return apiFetch<void>(`/api/groups/${id}/members/${userId}`, {
    method: "DELETE",
  });
}

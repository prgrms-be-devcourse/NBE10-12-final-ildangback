import { apiFetch } from "../../shared/api/client";
import type { SliceResponse } from "../../shared/api/types";
import type { ChatMessageResponse, ChatUnreadCountResponse } from "./types";

export function getGroupMessages(
  groupId: number,
  params: { cursor?: number; size?: number } = {},
) {
  const search = new URLSearchParams();
  if (params.cursor != null) search.set("cursor", String(params.cursor));
  search.set("size", String(params.size ?? 30));
  return apiFetch<SliceResponse<ChatMessageResponse>>(
    `/api/groups/${groupId}/messages?${search.toString()}`,
  );
}

export function markMessagesRead(groupId: number, lastReadMessageId: number) {
  return apiFetch<void>(`/api/groups/${groupId}/messages/read`, {
    method: "PUT",
    body: { lastReadMessageId },
  });
}

export function getUnreadCount(groupId: number) {
  return apiFetch<ChatUnreadCountResponse>(
    `/api/groups/${groupId}/messages/unread-count`,
  );
}

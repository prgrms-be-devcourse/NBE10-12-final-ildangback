import { apiFetch } from "../../shared/api/client";
import { tokenStore } from "../../shared/api/tokenStore";
import type { UserProfileResponse } from "../../shared/api/types";

export interface UpdateProfileRequest {
  nickname?: string;
  /** 비우려면 빈 문자열을 보낸다. null 은 "비우기" 가 아니라 "생략" 으로 처리된다. */
  introduction?: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export function getMyProfile(): Promise<UserProfileResponse> {
  return apiFetch("/api/users/me");
}

/** 수정할 필드만 담는다. 둘 다 없으면 서버가 400 을 준다. */
export function updateMyProfile(
  body: UpdateProfileRequest,
): Promise<UserProfileResponse> {
  return apiFetch("/api/users/me", { method: "PATCH", body });
}

export function changePassword(body: ChangePasswordRequest): Promise<void> {
  return apiFetch("/api/users/me/password", { method: "PATCH", body });
}

/**
 * 소프트 딜리트. 서버가 모든 RT 를 폐기하므로 로컬 토큰도 지운다.
 *
 * 비밀번호는 선택이다 — 소셜 전용 가입자는 비밀번호가 없어서 필수로 두면 영영 탈퇴하지
 * 못한다. 빈 문자열을 보내면 서버가 대조에 실패해 401 이 되므로 아예 키를 뺀다.
 */
export async function deleteAccount(password?: string): Promise<void> {
  await apiFetch<void>("/api/users/me", {
    method: "DELETE",
    body: password ? { password } : {},
  });
  tokenStore.clear();
}

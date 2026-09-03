import { apiFetch } from "../../shared/api/client";
import { tokenStore } from "../../shared/api/tokenStore";
import type {
  AvailabilityResponse,
  LoginResponse,
  UserSummaryResponse,
} from "../../shared/api/types";

export interface SignUpRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

/** 가입만 하고 토큰은 발급하지 않는다. 로그인은 별도 호출이다. */
export function signUp(body: SignUpRequest): Promise<UserSummaryResponse> {
  return apiFetch("/api/auth/signup", { method: "POST", body, auth: false });
}

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const result = await apiFetch<LoginResponse>("/api/auth/login", {
    method: "POST",
    body,
    auth: false,
  });
  tokenStore.set(result);
  return result;
}

/**
 * RT 1건을 폐기한다. 이미 폐기됐거나 없는 RT 여도 204 라 실패를 신경 쓸 필요가 없다.
 * 서버가 어떻게 응답하든 로컬 토큰은 지운다.
 *
 * 본문을 함수로 넘기는 이유: AT 가 만료된 채 로그아웃하면 401 → 갱신 → 재시도가
 * 돌면서 그 사이 RT 가 로테이션된다. 본문을 미리 만들어두면 재시도가 이미 폐기된
 * 옛 RT 를 보내 서버가 아무것도 안 하고, 새 RT 가 만료일까지 살아남는다.
 */
export async function logout(): Promise<void> {
  try {
    if (tokenStore.getRefreshToken()) {
      await apiFetch<void>("/api/auth/logout", {
        method: "POST",
        body: () => ({ refreshToken: tokenStore.getRefreshToken() }),
      });
    }
  } finally {
    tokenStore.clear();
  }
}

export function checkEmail(email: string): Promise<AvailabilityResponse> {
  return apiFetch(`/api/auth/check-email?email=${encodeURIComponent(email)}`, {
    auth: false,
  });
}

export function checkNickname(nickname: string): Promise<AvailabilityResponse> {
  return apiFetch(
    `/api/auth/check-nickname?nickname=${encodeURIComponent(nickname)}`,
    {
      auth: false,
    },
  );
}

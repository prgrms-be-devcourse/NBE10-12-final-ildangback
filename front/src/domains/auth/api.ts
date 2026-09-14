import { apiFetch } from "../../shared/api/client";
import { tokenStore } from "../../shared/api/tokenStore";
import type {
  AvailabilityResponse,
  LoginResponse,
  PasswordResetTargetResponse,
  UserSummaryResponse,
} from "../../shared/api/types";
import type { OAuthProviderId } from "./oauth";

export interface SignUpRequest {
  email: string;
  password: string;
  nickname: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface OAuthLoginRequest {
  code: string;
  state: string;
  redirectUri: string;
  codeVerifier: string;
}

export interface PasswordResetConfirmRequest {
  token: string;
  newPassword: string;
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

/** 가입과 로그인이 같은 엔드포인트다. 신규 가입이면 응답의 newUser 가 true 로 온다. */
export async function oauthLogin(
  provider: OAuthProviderId,
  body: OAuthLoginRequest,
): Promise<LoginResponse> {
  const result = await apiFetch<LoginResponse>(`/api/auth/oauth/${provider}`, {
    method: "POST",
    body,
    auth: false,
  });
  tokenStore.set(result);
  return result;
}

/** 60초 안에 다시 부르면 429, 이미 인증했으면 409 다. */
export function resendVerificationEmail(): Promise<void> {
  return apiFetch("/api/auth/verify-email/resend", { method: "POST" });
}

/**
 * 가입 여부도 인증 여부도 알려주지 않는다. 없는 주소여도 미인증이어도 최소 간격에
 * 걸려도 똑같이 204 라, 호출한 쪽은 성공 안내 하나만 띄우면 된다.
 */
export function requestPasswordReset(email: string): Promise<void> {
  return apiFetch("/api/auth/password-reset", {
    method: "POST",
    body: { email },
    auth: false,
  });
}

/** 재설정 화면이 열릴 때 부른다. 토큰을 소비하지 않는다. */
export function checkPasswordResetToken(
  token: string,
): Promise<PasswordResetTargetResponse> {
  return apiFetch(
    `/api/auth/password-reset?token=${encodeURIComponent(token)}`,
    { auth: false },
  );
}

/** 성공하면 서버가 본인의 모든 RT 를 폐기한다. 전 기기 재로그인이 필요하다. */
export function confirmPasswordReset(
  body: PasswordResetConfirmRequest,
): Promise<void> {
  return apiFetch("/api/auth/password-reset/confirm", {
    method: "POST",
    body,
    auth: false,
  });
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

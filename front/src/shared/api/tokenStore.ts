import type { TokenResponse } from "./types";

/**
 * 토큰 보관 한 곳.
 *
 * 인증인가설계.md 7-1 이 요구하는 격리 지점이다. 앱(웹뷰·KMP) 단계로 가면
 * RT 저장이 Keystore / Keychain 으로 바뀌는데, 그때 고칠 파일은 여기 하나여야 한다.
 * 다른 파일에서 localStorage 를 직접 만지지 않는다.
 *
 * AT 는 메모리에만 둔다 (인증인가설계.md 595). 새로고침하면 사라지므로
 * 앱 시작 시 RT 로 한 번 재발급받는다 — client.ts 가 알아서 한다.
 */
const REFRESH_TOKEN_KEY = "gommit.refreshToken";

let accessToken: string | null = null;

export const tokenStore = {
  getAccessToken(): string | null {
    return accessToken;
  },

  getRefreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },

  /** 재발급 응답의 refreshToken 은 매번 새 값이라 반드시 교체 저장해야 한다. */
  set(tokens: TokenResponse): void {
    accessToken = tokens.accessToken;
    localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
  },

  clear(): void {
    accessToken = null;
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};

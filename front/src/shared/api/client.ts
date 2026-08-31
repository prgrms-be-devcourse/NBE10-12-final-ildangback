import { tokenStore } from "./tokenStore";
import type { ErrorResponse, FieldError, TokenResponse } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "";

/** 서버가 ErrorResponse 를 준 경우. message 를 그대로 화면에 띄운다. */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly errors: FieldError[];

  constructor(status: number, body: ErrorResponse) {
    super(body.message);
    this.name = "ApiError";
    this.status = status;
    this.code = body.code;
    this.errors = body.errors ?? [];
  }

  /** 필드별 오류가 담겨 오는 유일한 코드다. 폼에서 인풋 밑에 붙일 때 쓴다. */
  get isValidationError(): boolean {
    return this.code === "INVALID_INPUT_VALUE" && this.errors.length > 0;
  }
}

/** RT 까지 죽어서 더 해볼 게 없는 상태. 로그인 화면으로 보낸다. */
export class SessionExpiredError extends Error {
  constructor() {
    super("세션이 만료되었습니다. 다시 로그인해 주세요.");
    this.name = "SessionExpiredError";
  }
}

let onSessionExpired: (() => void) | null = null;

/** AuthProvider 가 등록한다. 갱신이 최종 실패했을 때 한 번 불린다. */
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler;
}

// ---------------------------------------------------------------------------
// single-flight 재발급
//
// 인증인가설계.md 7-2 가 경고하는 지점이다. 401 마다 각자 refresh 를 부르면
// 첫 응답이 RT 를 로테이션시켜 나머지가 옛 RT 를 들고 401 을 맞는다.
// 진행 중인 갱신이 있으면 그 Promise 를 그대로 돌려줘서 호출을 1회로 묶는다.
// ---------------------------------------------------------------------------
let refreshInFlight: Promise<string> | null = null;

/**
 * 재발급에만 시한을 건다. single-flight 라 이 호출이 물리면 뒤따르는 요청이 전부
 * 같은 Promise 에 매달려 함께 멈춘다 — 일반 요청 하나가 늦는 것과 무게가 다르다.
 * 시한이 지나면 fetch 가 거부되고 아래 일반 네트워크 오류와 같은 길로 간다.
 */
const REFRESH_TIMEOUT_MS = 10_000;

async function requestNewTokens(): Promise<string> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) throw new SessionExpiredError();

  const response = await fetch(`${BASE_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
    signal: AbortSignal.timeout(REFRESH_TIMEOUT_MS),
  });

  if (!response.ok) {
    tokenStore.clear();
    throw new SessionExpiredError();
  }

  const tokens: TokenResponse = await response.json();
  tokenStore.set(tokens);
  return tokens.accessToken;
}

function refreshAccessToken(): Promise<string> {
  if (!refreshInFlight) {
    refreshInFlight = requestNewTokens().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

// ---------------------------------------------------------------------------

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  /**
   * 함수로 주면 보내기 직전에 평가한다. 401 → 갱신 → 재시도 경로에서 값이 바뀌는
   * 본문(RT)에 필요하다. 미리 만들어두면 재시도가 옛 RT 를 그대로 보낸다.
   */
  body?: unknown;
  /** 기본 true. 로그인·회원가입·재발급처럼 토큰이 필요 없는 호출만 false 로 준다. */
  auth?: boolean;
}

async function send(
  path: string,
  options: RequestOptions,
  accessToken: string | null,
) {
  const body =
    typeof options.body === "function"
      ? (options.body as () => unknown)()
      : options.body;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

  return fetch(`${BASE_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const needsAuth = options.auth !== false;

  // AT 는 메모리에만 있어서 새로고침하면 없다. RT 가 남아 있으면 먼저 채운다.
  // 일부러 401 을 맞아서 갱신하는 것보다 요청 1회가 덜 든다.
  if (
    needsAuth &&
    !tokenStore.getAccessToken() &&
    tokenStore.getRefreshToken()
  ) {
    await refreshAccessTokenOrExpire();
  }

  let response = await send(
    path,
    options,
    needsAuth ? tokenStore.getAccessToken() : null,
  );

  if (response.status === 401 && needsAuth && tokenStore.getRefreshToken()) {
    const body = await readErrorBody(response);

    // 401 이라고 다 AT 문제가 아니다. 비밀번호 변경 · 탈퇴는 현재 비밀번호가 틀려도
    // 401 INVALID_CREDENTIALS 로 온다(api.yaml 이 "code로 구분한다"고 못박아 뒀다).
    // 그걸 만료로 착각해 갱신을 돌리면 RT 만 헛되이 로테이션된다.
    if (body.code !== "UNAUTHORIZED") {
      throw new ApiError(401, body);
    }

    // 여기부터가 진짜 AT 만료다. 갱신은 single-flight 라 동시에 여러 개가 터져도 1회만 나간다.
    const renewed = await refreshAccessTokenOrExpire();
    response = await send(path, options, renewed);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorBody(response));
  }

  // 로그아웃·탈퇴는 204 라 본문이 없다.
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

async function refreshAccessTokenOrExpire(): Promise<string> {
  try {
    return await refreshAccessToken();
  } catch (error) {
    // 갱신도 실패했으면 재시도하지 않는다. 무한루프 방지 (인증인가설계.md 7장).
    onSessionExpired?.();
    throw error;
  }
}

async function readErrorBody(response: Response): Promise<ErrorResponse> {
  try {
    return (await response.json()) as ErrorResponse;
  } catch {
    // 서버가 죽었거나 프록시가 HTML 을 뱉은 경우. 화면에 띄울 문장은 있어야 한다.
    return {
      code: "UNKNOWN",
      message: "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
      errors: [],
    };
  }
}

/**
 * 소셜 로그인의 브라우저 쪽 절반.
 *
 * state 와 PKCE 는 프론트가 만들고 프론트가 대조한다 — 백엔드는 state 를 검증하지 않는다
 * (네이버 토큰 엔드포인트가 필수로 요구해서 그대로 넘길 뿐이다).
 * 즉 CSRF 방어가 이 파일에 걸려 있다.
 */

export type OAuthProviderId = "google" | "naver";

interface ProviderConfig {
  label: string;
  authorizeUri: string;
  clientId: string;
  /** 네이버는 동의 항목을 개발자센터 콘솔에서 정해서 인가 요청에 scope 를 싣지 않는다. */
  scope?: string;
}

const PROVIDERS: Record<OAuthProviderId, ProviderConfig> = {
  google: {
    label: "Google",
    authorizeUri: "https://accounts.google.com/o/oauth2/v2/auth",
    clientId: import.meta.env.VITE_GOOGLE_CLIENT_ID,
    scope: "openid email profile",
  },
  naver: {
    label: "네이버",
    authorizeUri: "https://nid.naver.com/oauth2.0/authorize",
    clientId: import.meta.env.VITE_NAVER_CLIENT_ID,
  },
};

export function isOAuthProvider(value: string): value is OAuthProviderId {
  return value === "google" || value === "naver";
}

export function providerLabel(provider: OAuthProviderId): string {
  return PROVIDERS[provider].label;
}

/**
 * 콘솔 등록과 서버 화이트리스트(oauth.allowed-redirect-uris)에 이 값이 그대로 들어가 있어야 한다.
 * 인가 요청과 토큰 교환에서 한 글자라도 다르면 프로바이더가 거절한다.
 */
export function redirectUriOf(provider: OAuthProviderId): string {
  return `${window.location.origin}/oauth/${provider}/callback`;
}

// ---------------------------------------------------------------------------
// 안드로이드 앱 웹뷰인가
//
// 앱 셸이 웹뷰 UA 뒤에 접미를 붙인다. 붙이는 쪽은 MainActivity 의
// APP_USER_AGENT_SUFFIX 다 — 값을 바꾸면 양쪽을 같이 바꿔야 한다.
//
// 이 판별을 두 곳에서 쓴다. startOAuth 는 state 에 접두어를 붙일지 정하고,
// 콜백 페이지는 커스텀 스킴으로 튕길지 정한다. 자세한 것은 docs/앱설계.md 4-1.
// ---------------------------------------------------------------------------

const APP_USER_AGENT_SUFFIX = "Gommit-App/1.0";

/** 앱에서 시작한 로그인이라는 표시. OAuth 왕복을 타고 넘어간다. */
const APP_STATE_PREFIX = "app:";

export function isAppWebView(): boolean {
  return navigator.userAgent.includes(APP_USER_AGENT_SUFFIX);
}

export function isAppState(state: string | null): boolean {
  return state !== null && state.startsWith(APP_STATE_PREFIX);
}

/** 앱이 인텐트로 받는 주소. 경로 마지막 칸의 프로바이더를 앱이 읽는다. */
export function appCallbackUrl(provider: string, query: string): string {
  return `gommit://oauth/callback/${provider}${query}`;
}

// ---------------------------------------------------------------------------
// PKCE
// ---------------------------------------------------------------------------

function base64Url(bytes: Uint8Array): string {
  const binary = String.fromCharCode(...bytes);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function randomToken(): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(32)));
}

async function challengeOf(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(verifier),
  );
  return base64Url(new Uint8Array(digest));
}

// ---------------------------------------------------------------------------
// 진행 중인 인가 요청
//
// 프로바이더 페이지로 나갔다가 콜백으로 돌아오는 사이 앱이 통째로 다시 뜬다.
// sessionStorage 라 탭을 닫으면 사라지고, 같은 탭으로 돌아오면 남아 있다.
// ---------------------------------------------------------------------------

const PENDING_KEY = "gommit.oauth";

interface PendingOAuth {
  provider: OAuthProviderId;
  state: string;
  codeVerifier: string;
}

/**
 * 읽으면서 지운다. StrictMode 는 개발 모드에서 effect 를 두 번 돌리는데 인가 코드는
 * 1회용이라, 두 번째 실행이 아무것도 못 찾고 멈춰야 한다.
 */
export function takePendingOAuth(): PendingOAuth | null {
  const raw = sessionStorage.getItem(PENDING_KEY);
  sessionStorage.removeItem(PENDING_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as PendingOAuth;
  } catch {
    return null;
  }
}

/** 프로바이더 인증 페이지로 나간다. 돌아오는 곳은 /oauth/{provider}/callback 이다. */
export async function startOAuth(provider: OAuthProviderId): Promise<void> {
  const config = PROVIDERS[provider];
  // 클라이언트 ID 가 비어 있으면 프로바이더의 오류 페이지로 나갔다가 돌아오지 못한다.
  // 나가기 전에 멈춰서 호출한 쪽이 안내를 띄우게 한다.
  if (!config.clientId) {
    throw new Error(`${provider} client id is not configured`);
  }

  // 앱에서 시작했으면 접두어를 붙인다. 프로바이더가 state 를 그대로 돌려주므로
  // 콜백 페이지가 이걸 보고 앱으로 튕겨야 하는 왕복인지 안다.
  const state = (isAppWebView() ? APP_STATE_PREFIX : "") + randomToken();
  const codeVerifier = randomToken();

  const pending: PendingOAuth = { provider, state, codeVerifier };
  sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending));

  const params = new URLSearchParams({
    response_type: "code",
    client_id: config.clientId,
    redirect_uri: redirectUriOf(provider),
    state,
    code_challenge: await challengeOf(codeVerifier),
    code_challenge_method: "S256",
  });
  if (config.scope) params.set("scope", config.scope);

  window.location.assign(`${config.authorizeUri}?${params}`);
}

/**
 * 화면을 넘어가며 한 번만 보여줄 안내 문구.
 *
 * 라우터 state 로 넘기면 RequireAuth 의 리다이렉트와 경쟁한다 — 로그인 상태가 비는 순간
 * RequireAuth 가 /login 으로 튕겨내면서 state 를 자기 것으로 덮어써 문구가 사라진다.
 * 모듈 변수에 두면 어느 쪽이 이기든 남는다.
 */
let pending: string | null = null;

export function setFlash(message: string): void {
  pending = message;
}

/** 읽으면서 비운다. 뒤로 가기로 돌아와도 다시 뜨지 않는다. */
export function takeFlash(): string | null {
  const message = pending;
  pending = null;
  return message;
}

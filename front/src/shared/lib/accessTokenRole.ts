import { tokenStore } from "../api/tokenStore";
import type { UserRole } from "../api/types";

/**
 * AT 의 `role` 클레임을 읽는다. `/admin` 진입 판정에만 쓴다.
 *
 * 서버가 토큰에 이미 넣어 주는 값이라(`JwtProvider.issue(userId, role)`) 응답 DTO 에
 * role 을 추가하지 않아도 된다. 값은 enum 이름 그대로 "USER" 또는 "ADMIN" 이다
 * (Spring 이 붙이는 `ROLE_` 접두어는 서버 안에서만 쓴다).
 *
 * **서명을 검증하지 않는다. 보안이 아니다.** 페이로드는 브라우저가 이미 들고 있는
 * 값이고 사용자가 고칠 수도 있다. 실제 차단은 서버가 `/api/admin/**` 을
 * `hasRole("ADMIN")` 으로 하고, 여기는 권한 없는 사람이 깨진 화면을 보지 않게 하는
 * 역할만 한다.
 */
export function accessTokenRole(): UserRole | null {
  const token = tokenStore.getAccessToken();
  if (!token) return null;

  const role = claim(token, "role");
  return role === "ADMIN" || role === "USER" ? role : null;
}

function claim(token: string, name: string): unknown {
  const payload = token.split(".")[1];
  if (!payload) return undefined;
  try {
    // JWT 는 base64url 이라 base64 문자로 바꾸고 패딩을 채워야 atob 이 받는다.
    const base64 = payload.replaceAll("-", "+").replaceAll("_", "/");
    const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    return (JSON.parse(atob(padded)) as Record<string, unknown>)[name];
  } catch {
    // 토큰 모양이 예상과 다르면 권한 없음으로 본다.
    return undefined;
  }
}

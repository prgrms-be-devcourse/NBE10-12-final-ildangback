import { Outlet } from "react-router";
import { accessTokenRole } from "../../shared/lib/accessTokenRole";
import { useAuth } from "../../shared/lib/useAuth";
import { LoadingScreen } from "../../shared/ui/LoadingScreen";
import { NotFoundPage } from "../../pages/NotFoundPage";

/**
 * `/admin` 아래 화면을 관리자에게만 그린다. `RequireAuth` 안쪽에 겹쳐 쓴다.
 *
 * 권한은 AT 의 role 클레임에서 읽는다 — 서버가 토큰에 이미 넣어 주는 값이라
 * 응답 DTO 를 건드리지 않아도 된다. `RequireAuth` 가 통과시킨 뒤에만 그려지므로
 * 그 시점에는 AT 가 있다.
 *
 * **이것은 보안이 아니다.** 프론트는 Cloudflare 가 어떤 경로든 index.html 로 돌려주고
 * JS 번들도 공개라, 관리자 경로가 있다는 사실은 숨겨지지 않는다. 토큰 페이로드도
 * 사용자가 고칠 수 있다. 실제 차단은 서버가 `/api/admin/**` 을 `hasRole("ADMIN")` 으로
 * 하는 것이고, 여기는 권한 없는 사람이 401·403 으로 깨진 화면을 보지 않게 하는 역할만 한다.
 *
 * 그래서 "관리자 전용입니다" 안내 대신 404 를 보여준다 — 안내를 띄우면 그 경로가
 * 존재한다고 확인해 주는 셈이다.
 */
export function RequireAdmin() {
  const { status } = useAuth();

  if (status === "loading") return <LoadingScreen />;
  if (accessTokenRole() !== "ADMIN") return <NotFoundPage />;
  return <Outlet />;
}

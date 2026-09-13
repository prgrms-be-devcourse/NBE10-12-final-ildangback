import { Navigate, Outlet, useLocation } from "react-router";
import { useAuth } from "../shared/lib/useAuth";
import { LoadingScreen } from "../shared/ui/LoadingScreen";

/**
 * 로그인해야만 열리는 화면(프로필 수정 · 비밀번호 변경 · 탈퇴)에만 쓴다.
 *
 * 탭 3개는 여기 감싸지 않는다. 시안상 비로그인도 들어갈 수 있고 빈 상태 + 가입 유도가
 * 뜨는 구조다. 튕겨내면 시안과 다른 앱이 된다.
 */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === "loading") return <LoadingScreen />;
  if (status === "anonymous") {
    // 이미 다른 곳으로 나가는 중이면 끼어들지 않는다. 주소는 바뀌었는데 라우터가 아직
    // 커밋을 안 한 순간이라 이 화면이 그려져 있다 — 여기서 튕겨내면 state.from 에
    // 떠나는 화면이 실려 재로그인 때 그리로 돌아간다.
    if (window.location.pathname !== location.pathname) return null;
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}

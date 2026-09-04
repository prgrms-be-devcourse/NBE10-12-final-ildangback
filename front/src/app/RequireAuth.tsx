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
  if (status === "anonymous")
    return <Navigate to="/login" replace state={{ from: location }} />;
  return <Outlet />;
}

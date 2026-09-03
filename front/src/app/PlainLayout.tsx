import { Outlet } from "react-router";
import { MobileShell } from "./MobileShell";

/**
 * 하단바가 없는 화면들. 상단바는 각 페이지가 TopBar 로 직접 그린다 —
 * 로그인 · 회원가입은 뒤로가기만, 설정 같은 하위 화면은 가운데 제목까지 필요해서다.
 */
export function PlainLayout() {
  return (
    <MobileShell>
      <Outlet />
    </MobileShell>
  );
}

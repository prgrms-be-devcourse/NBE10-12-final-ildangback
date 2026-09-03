import { Outlet } from "react-router";
import { BottomTabBar } from "./BottomTabBar";
import { MobileShell } from "./MobileShell";

/** 홈 · 챌린지 · 프로필. 하단바가 붙는 화면들이다. */
export function TabLayout() {
  return (
    <MobileShell>
      <div className="flex-1">
        <Outlet />
      </div>
      <BottomTabBar />
    </MobileShell>
  );
}

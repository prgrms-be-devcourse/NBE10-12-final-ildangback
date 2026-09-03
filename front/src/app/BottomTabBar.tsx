import { HouseIcon, TrophyIcon, UserIcon } from "@phosphor-icons/react";
import type { Icon } from "@phosphor-icons/react";
import { NavLink } from "react-router";

const TABS: { to: string; label: string; icon: Icon }[] = [
  { to: "/", label: "홈", icon: HouseIcon },
  { to: "/challenges", label: "챌린지", icon: TrophyIcon },
  { to: "/profile", label: "프로필", icon: UserIcon },
];

export function BottomTabBar() {
  return (
    <nav className="sticky bottom-0 grid grid-cols-3 border-t border-purple-100 bg-white pt-2 pb-[max(0.5rem,env(safe-area-inset-bottom))]">
      {TABS.map(({ to, label, icon: TabIcon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === "/"}
          className="flex flex-col items-center gap-1"
        >
          {({ isActive }) => (
            <>
              {/* 활성 탭은 채워진 아이콘이다 (하단바 시안). phosphor 는 같은 아이콘에 weight 를 준다. */}
              <TabIcon
                size={24}
                weight={isActive ? "fill" : "regular"}
                className={isActive ? "text-purple-500" : "text-gray-500"}
              />
              <span
                className={`text-[11px] ${isActive ? "font-semibold text-purple-500" : "text-gray-500"}`}
              >
                {label}
              </span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}

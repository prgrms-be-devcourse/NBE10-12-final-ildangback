import { CaretRightIcon, TShirtIcon } from "@phosphor-icons/react";
import { Link } from "react-router";
import { TopBar } from "../../../shared/ui/TopBar";

/**
 * 관리자 허브. `/admin` 으로 직접 들어온다 — 앱 어디에도 링크를 두지 않는다.
 *
 * 관리 영역이 늘면 아래 AREAS 에 한 줄을 더한다. 신고와 이의제기는 백엔드가 아직 없고
 * (`docs/신고이의제기명세.md` 는 명세만), 그룹 배경 관리는 feat/39 브랜치에 있어 main 에
 * 없다. 엔드포인트가 머지된 뒤에 추가한다 — 지금 껍데기 화면을 미리 만들지 않는다.
 */
const AREAS = [
  {
    to: "/admin/items",
    icon: TShirtIcon,
    title: "아이템",
    description: "캐릭터 아이템 등록과 삭제",
  },
];

export function AdminHomePage() {
  return (
    <>
      <TopBar title="관리자" />

      <div className="flex-1 px-5 pb-10">
        <ul className="mt-2 space-y-3">
          {AREAS.map(({ to, icon: Icon, title, description }) => (
            <li key={to}>
              <Link
                to={to}
                className="flex items-center gap-3 rounded-2xl border border-purple-200 p-4"
              >
                <Icon size={28} weight="fill" className="text-purple-400" />
                <span className="flex-1">
                  <span className="block text-[15px] font-bold text-gray-900">
                    {title}
                  </span>
                  <span className="block text-[13px] text-gray-500">
                    {description}
                  </span>
                </span>
                <CaretRightIcon
                  size={18}
                  className="text-gray-400"
                  aria-hidden
                />
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}

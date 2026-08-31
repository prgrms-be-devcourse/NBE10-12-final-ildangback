import { CaretRightIcon, CoatHangerIcon } from "@phosphor-icons/react";
import { useNavigate } from "react-router";
import profileCharacterRoom from "../../../assets/illustrations/profile-character-room.webp";
import profileHero from "../../../assets/illustrations/profile-hero.webp";
import iconUnauthCharacter from "../../../assets/icons/profile-unauth-character.webp";
import iconUnauthStats from "../../../assets/icons/profile-unauth-stats.webp";
import iconUnauthArchive from "../../../assets/icons/profile-unauth-archive.webp";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { PageHeader } from "../../../shared/ui/PageHeader";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { SignUpPrompt } from "../../../shared/ui/SignUpPrompt";

import { useToast } from "../../../shared/lib/useToast";

const MENU: { icon: string; label: string; to?: string }[] = [
  { icon: pixelIcons.personalStats, label: "개인 통계" },
  { icon: pixelIcons.monthlyMergeArchive, label: "월간 머지 아카이브" },
  { icon: pixelIcons.pointHistory, label: "포인트 내역" },
  { icon: pixelIcons.badgeAchievement, label: "참여했던 챌린지" },
  { icon: pixelIcons.settingsGear, label: "설정", to: "/profile/settings" },
];

export function ProfilePage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  if (!user) {
    return (
      <>
        <PageHeader title="프로필" />
        <SignUpPrompt
          title="나만의 기록을 만들어보세요"
          description={
            "회원가입하면 캐릭터와 인증 기록을\n한곳에서 관리할 수 있어요"
          }
          illustration={
            <img
              src={profileHero}
              alt="프로필 일러스트"
              className="h-36 object-contain pixelated"
            />
          }
          features={[
            { icon: iconUnauthCharacter, label: "나만의 캐릭터 꾸미기" },
            { icon: iconUnauthStats, label: "꼬밋 잔디와 개인 통계" },
            { icon: iconUnauthArchive, label: "월간 머지 아카이브" },
          ]}
          cardBg="bg-[#F7F5FC]"
        />
      </>
    );
  }

  return (
    <>
      <PageHeader title="프로필" />

      <div className="px-5 pb-10">
        <section className="mt-2 rounded-2xl border border-purple-200 p-3">
          <div className="flex h-56 items-center justify-center overflow-hidden rounded-xl bg-purple-50">
            <img
              src={profileCharacterRoom}
              alt="캐릭터 룸"
              className="h-full w-full object-cover pixelated"
            />
          </div>
          <Button
            type="button"
            onClick={() =>
              showToast("캐릭터 꾸미기 기능은 다음 업데이트에 오픈됩니다.")
            }
            className="mt-3 flex items-center justify-center gap-2.5 text-[17px] font-bold"
          >
            <CoatHangerIcon size={24} weight="bold" />
            <span>캐릭터 꾸미기</span>
          </Button>
        </section>

        <nav className="mt-5 overflow-hidden rounded-2xl border border-purple-200 bg-white">
          {MENU.map(({ icon, label, to }) => (
            <button
              key={label}
              type="button"
              onClick={
                to
                  ? () => navigate(to)
                  : () =>
                      showToast(`${label} 기능은 다음 업데이트에 오픈됩니다.`)
              }
              className="flex w-full items-center gap-4 border-b border-purple-100 px-5 py-2.5 text-left last:border-b-0 transition-colors hover:bg-purple-50/50"
            >
              <PixelIcon src={icon} size={36} />
              <span className="flex-1 text-[15px] font-semibold text-gray-900">
                {label}
              </span>
              <CaretRightIcon size={18} className="text-gray-400" aria-hidden />
            </button>
          ))}
        </nav>
      </div>
    </>
  );
}

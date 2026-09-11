import { CaretRightIcon, CoatHangerIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import profileCharacterRoom from "../../../assets/illustrations/profile-character-room.webp";
import profileHero from "../../../assets/illustrations/profile-hero.webp";
import iconUnauthCharacter from "../../../assets/icons/profile-unauth-character.webp";
import iconUnauthStats from "../../../assets/icons/profile-unauth-stats.webp";
import iconUnauthArchive from "../../../assets/icons/profile-unauth-archive.webp";
import { getMyCharacter } from "../../item/api";
import { CharacterView } from "../../item/components/CharacterView";
import { toCharacterArt } from "../../item/lib/shop";
import type { ItemSlot } from "../../../shared/api/types";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { PageHeader } from "../../../shared/ui/PageHeader";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { SignUpPrompt } from "../../../shared/ui/SignUpPrompt";

import { useToast } from "../../../shared/lib/useToast";

const MENU: { icon: string; label: string; to?: string }[] = [
  { icon: pixelIcons.personalStats, label: "개인 통계", to: "/profile/stats" },
  {
    icon: pixelIcons.monthlyMergeArchive,
    label: "월간 머지 아카이브",
    to: "/profile/monthly-merges",
  },
  {
    icon: pixelIcons.pointHistory,
    label: "포인트 내역",
    to: "/profile/points",
  },
  {
    icon: pixelIcons.badgeAchievement,
    label: "참여했던 챌린지",
    to: "/profile/challenges",
  },
  { icon: pixelIcons.settingsGear, label: "설정", to: "/profile/settings" },
];

export function ProfilePage() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();

  // null 이면 아직 못 받은 것이고 {} 는 받았는데 아무것도 안 낀 것이다.
  // 둘을 가르지 않으면 옷을 입은 사람에게도 맨몸이 잠깐 보였다가 바뀐다.
  const [characterArt, setCharacterArt] = useState<Partial<
    Record<ItemSlot, string>
  > | null>(null);

  // 이 화면은 상점과 달리 아이템 목록이 없어서 캐릭터를 따로 받아야 한다.
  const userId = user?.id;
  useEffect(() => {
    if (!userId) return;

    let cancelled = false;
    getMyCharacter()
      .then(({ slots }) => {
        if (!cancelled) setCharacterArt(toCharacterArt(slots));
      })
      .catch(() => {
        // 못 받아도 기본 몸통은 그린다. 이 화면의 본 내용은 아래 메뉴다.
        if (!cancelled) setCharacterArt({});
      });

    return () => {
      cancelled = true;
    };
  }, [userId]);

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
          <div className="relative h-56 overflow-hidden rounded-xl bg-purple-50">
            <img
              src={profileCharacterRoom}
              alt=""
              className="h-full w-full object-cover pixelated"
              aria-hidden
            />
            {/* 배경판 러그 한가운데에 세운다. */}
            {characterArt && (
              <span className="absolute bottom-[13px] left-1/2 block h-[204px] w-[204px] -translate-x-1/2">
                <CharacterView art={characterArt} label="내 캐릭터" />
              </span>
            )}
          </div>
          <Button
            type="button"
            onClick={() => navigate("/profile/shop")}
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

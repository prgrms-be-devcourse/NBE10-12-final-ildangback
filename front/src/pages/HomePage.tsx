import { CheckIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import homeChangesCards from "../assets/illustrations/home-changes-cards.webp";
import homeHeroCard from "../assets/illustrations/home-hero-card.webp";
import { fetchHome, type HomeData } from "../domains/home/api";
import { CharacterView } from "../domains/item/components/CharacterView";
import { toCharacterArt } from "../domains/item/lib/shop";
import type { TodayChallengeResponse } from "../shared/api/types";
import { formatMonthDay } from "../shared/lib/date";
import { useAuth } from "../shared/lib/useAuth";
import { useToast } from "../shared/lib/useToast";
import { CATEGORY_ART, CATEGORY_ICON } from "../shared/ui/categoryIcons";
import { designArt } from "../shared/ui/designArt";
import { ContributionGrid } from "../shared/ui/ContributionGrid";
import { Logo } from "../shared/ui/Logo";
import { PixelIcon } from "../shared/ui/PixelIcon";
import { pixelIcons } from "../shared/ui/pixelIcons";

// 시안 393px 프레임에서 가져온 값이다. 가이드에 없는 색만 여기 둔다.
const DEEP = "#794CC7";
const BUBBLE = "#EBE3F6";
const CARD = "#FEFEFE";
const PILL = "#F6F1FF";
const DONE = "#16A300";

// 홈은 미리보기다. 카드 높이가 세 줄에 맞춰져 있다.
const RECENT_LIMIT = 3;

// 홈 잔디는 4개월치다. 개인 통계가 1년을 보여준다.
const GRASS_WEEKS = 18;

// 상태 메시지를 아직 안 쓴 사람에게 보여줄 문구.
const STATUS_PLACEHOLDER = "오늘도 한 칸 채우러 갑니다";

export function HomePage() {
  const { user } = useAuth();

  if (!user) return <AnonymousHome />;
  return <SignedInHome />;
}

function SignedInHome() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [data, setData] = useState<HomeData | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchHome(GRASS_WEEKS)
      .then((loaded) => {
        if (!cancelled) setData(loaded);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loading) {
    return (
      <p className="px-[22px] py-20 text-center text-[13px] text-gray-500">
        불러오는 중…
      </p>
    );
  }

  if (failed || !data || !user) {
    return (
      <p className="px-[22px] py-20 text-center text-[13px] text-gray-500">
        홈을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
      </p>
    );
  }

  const { home, grass } = data;
  const characterArt = toCharacterArt(home.character.slots);
  // 카드 높이와 점 잇는 세로선이 세 줄에 맞춰져 있다. 나머지는 "전체 보기" 로 간다.
  const recentActivities = data.activities.slice(0, RECENT_LIMIT);

  return (
    <div className="px-[22px] pt-[7px] pb-10">
      <header className="flex items-center justify-between">
        <Logo className="-ml-3 h-[51px]" />
        <button
          type="button"
          onClick={() => showToast("알림 기능은 다음 업데이트에 오픈됩니다.")}
          aria-label="알림"
          className="relative rounded-lg p-1.5 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none after:absolute after:-inset-[5px] after:content-['']"
        >
          <PixelIcon src={designArt.notificationBell} size={28} />
          {home.hasUnreadNotification && (
            <span
              className="absolute top-[4px] right-[6px] h-[7px] w-[7px] rounded-full"
              style={{ backgroundColor: DEEP }}
            />
          )}
        </button>
      </header>

      <div className="mt-[9px] flex items-center justify-between gap-2">
        <h2 className="text-[18px] leading-[22px] font-bold text-gray-900">
          {home.nickname}님, 오늘도 꼬밋해볼까요?
        </h2>
        <button
          type="button"
          onClick={() => navigate("/profile/points")}
          className="relative flex h-[27px] w-[78px] shrink-0 items-center justify-center gap-[5px] rounded-full focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none after:absolute after:inset-x-0 after:-inset-y-[9px] after:content-['']"
          style={{ backgroundColor: PILL }}
        >
          <PixelIcon src={pixelIcons.pointHistory} size={15} />
          <span className="text-[14px] leading-none font-bold text-purple-500">
            {home.pointBalance.toLocaleString()}P
          </span>
        </button>
      </div>

      {/* 시안 배치. 캐릭터가 지표 줄 왼쪽에 서고, 말풍선이 그 아래로 붙는다. */}
      <section className="mt-[9px] flex items-center">
        {/* 아이템 그림이 1080x1080 정사각 캔버스라 상자도 정사각이어야 겹쳐진다. */}
        <span className="-ml-[15px] block h-[76px] w-[76px] shrink-0">
          <CharacterView art={characterArt} label="내 캐릭터" />
        </span>
        <div className="flex flex-1 items-center justify-center gap-[10px]">
          <SummaryStat
            icon={designArt.statStreak}
            label="연속 인증"
            value={home.summary.personalStreak}
            unit="일"
          />
          <span className="h-[29px] w-px shrink-0 bg-[#EDEDED]" />
          <SummaryStat
            icon={designArt.statMonthly}
            label="이번 달 인증"
            value={home.summary.monthlyCheckInCount}
            unit="회"
          />
          <span className="h-[29px] w-px shrink-0 bg-[#EDEDED]" />
          <SummaryStat
            icon={designArt.statRate}
            label="이번 달 성공률"
            value={home.summary.monthlyCompletionRate}
            unit="%"
          />
        </div>
      </section>

      <div className="relative mt-[22px]">
        {/* 말풍선 꼭지. 위쪽 캐릭터를 가리킨다. */}
        <span
          className="absolute -top-[10px] left-[20px] h-0 w-0 border-r-[9px] border-b-[11px] border-l-[9px] border-r-transparent border-l-transparent"
          style={{ borderBottomColor: BUBBLE }}
          aria-hidden
        />
        <button
          type="button"
          onClick={() => navigate("/profile/edit")}
          className="relative flex h-[46px] w-full items-center rounded-[10px] border border-purple-200 px-[14px] focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
          style={{ backgroundColor: BUBBLE }}
        >
          <QuoteMark />
          <span className="ml-[8px] min-w-0 flex-1 truncate text-left text-[13px] text-[#4F4F4F]">
            {home.statusMessage || STATUS_PLACEHOLDER}
          </span>
          <PencilMark />
        </button>
      </div>

      <section className="mt-[28px]">
        <div className="flex items-end justify-between">
          <h3 className="text-[14px] leading-none font-bold text-gray-900">
            오늘의 챌린지
          </h3>
          <p className="text-[11px] leading-none text-gray-500">
            오늘 {home.todayTotalCount}개 중 {home.todayCompletedCount}개 완료
          </p>
        </div>

        <ul
          className="mt-[12px] flex min-h-[150px] flex-col overflow-hidden rounded-[10px] border border-purple-200"
          style={{ backgroundColor: CARD }}
        >
          {home.todayChallenges.length === 0 && (
            <li className="flex flex-1 items-center justify-center text-[12px] text-gray-500">
              오늘 인증할 챌린지가 없어요.
            </li>
          )}
          {home.todayChallenges.map((challenge) => (
            <li
              key={challenge.challengeId}
              className="flex-1 border-b border-purple-200 last:border-b-0"
            >
              <TodayChallengeRow
                challenge={challenge}
                onCheckIn={() =>
                  showToast("인증 기능은 다음 업데이트에 오픈됩니다.")
                }
              />
            </li>
          ))}
        </ul>
      </section>

      <section className="mt-[28px]">
        <h3 className="text-[14px] leading-none font-bold text-gray-900">
          꼬밋 잔디
        </h3>
        <div
          className="mt-[12px] rounded-[10px] border border-purple-200 px-[14px] py-[16px]"
          style={{ backgroundColor: CARD }}
        >
          <ContributionGrid days={grass} gap={5} showDayLabels interactive />
        </div>
      </section>

      <section className="mt-[28px]">
        <div className="flex items-end justify-between">
          <h3 className="text-[14px] leading-none font-bold text-gray-900">
            최근 활동
          </h3>
          <button
            type="button"
            onClick={() => navigate("/profile/check-ins")}
            className="relative rounded text-[12px] leading-none font-semibold focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none after:absolute after:-inset-x-2 after:-inset-y-4 after:content-['']"
            style={{ color: DEEP }}
          >
            전체 보기
          </button>
        </div>

        <ul
          className="relative mt-[12px] flex h-[104px] flex-col rounded-[10px] border border-purple-200"
          style={{ backgroundColor: CARD }}
        >
          {/* 점을 잇는 세로선. 점이 없으면 그리지 않는다. */}
          {recentActivities.length > 0 && (
            <span
              className="absolute top-[17px] left-[20px] h-[70px] w-px bg-purple-200"
              aria-hidden
            />
          )}
          {recentActivities.length === 0 && (
            <li className="flex flex-1 items-center justify-center text-[12px] text-gray-500">
              아직 활동 기록이 없어요.
            </li>
          )}
          {recentActivities.map((activity) => (
            <li
              key={`${activity.occurredAt}-${activity.title}`}
              className="relative flex flex-1 items-center pr-[16px] pl-[16px] after:absolute after:right-[16px] after:bottom-0 after:left-[44px] after:h-px after:bg-purple-100 after:content-[''] last:after:hidden"
            >
              <span className="relative h-2.5 w-2.5 shrink-0 rounded-full border border-purple-500 bg-white" />
              <span
                className="ml-[18px] shrink-0 text-[12px] font-semibold"
                style={{ color: DEEP }}
              >
                {activity.commitPrefix}
              </span>
              <span className="ml-[6px] min-w-0 flex-1 truncate text-[12px] text-gray-900">
                {activity.title}
              </span>
              <span className="shrink-0 text-[11px] text-gray-500 tabular-nums">
                {formatMonthDay(activity.occurredAt.slice(0, 10))}
              </span>
              <span
                className="ml-[14px] w-[34px] shrink-0 text-right text-[12px] font-semibold tabular-nums"
                style={{ color: DEEP }}
              >
                {activity.pointAmount > 0 ? "+" : ""}
                {activity.pointAmount}P
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function SummaryStat({
  icon,
  label,
  value,
  unit,
}: {
  icon: string;
  label: string;
  value: number;
  unit: string;
}) {
  return (
    <div className="flex shrink-0 items-center gap-[6px]">
      <PixelIcon src={icon} size={18} />
      <div>
        <p className="text-[11px] leading-none whitespace-nowrap text-gray-500">
          {label}
        </p>
        <p className="mt-[7px] leading-none tabular-nums">
          <span className="text-[18px] font-bold text-gray-900">{value}</span>
          <span className="ml-px text-[11px] text-gray-500">{unit}</span>
        </p>
      </div>
    </div>
  );
}

function TodayChallengeRow({
  challenge,
  onCheckIn,
}: {
  challenge: TodayChallengeResponse;
  onCheckIn(): void;
}) {
  const art = CATEGORY_ART[challenge.category];
  const CategoryIcon = CATEGORY_ICON[challenge.category];

  return (
    <div className="flex h-full items-center gap-[10px] pr-[12px] pl-[12px]">
      <span className="flex w-[42px] shrink-0 justify-center text-purple-500">
        {art ? (
          <img src={art} alt="" className="h-[40px] object-contain pixelated" />
        ) : (
          <CategoryIcon size={34} weight="fill" />
        )}
      </span>

      <span className="min-w-0 flex-1 truncate text-[14px] font-semibold text-gray-900">
        {challenge.title}
      </span>
      <span className="w-[30px] shrink-0 text-right text-[11px] text-gray-500 tabular-nums">
        {challenge.currentCount}/{challenge.targetCount}
      </span>

      {challenge.completed ? (
        <span
          className="flex w-[53px] shrink-0 items-center justify-center gap-[3px] text-[12px] font-semibold"
          style={{ color: DONE }}
        >
          <CheckIcon size={12} weight="bold" aria-hidden />
          완료
        </span>
      ) : (
        <button
          type="button"
          onClick={onCheckIn}
          className="relative h-[21px] w-[53px] shrink-0 rounded-[4.5px] border text-[12px] font-semibold focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none after:absolute after:-inset-x-1 after:-inset-y-3 after:content-['']"
          style={{ borderColor: DEEP, color: DEEP, backgroundColor: CARD }}
        >
          인증하기
        </button>
      )}
    </div>
  );
}

function QuoteMark() {
  return (
    <svg
      width="14"
      height="12"
      viewBox="0 0 14 12"
      fill="none"
      className="shrink-0"
      aria-hidden
    >
      <path
        d="M0 12V7.2C0 5.28 0.44 3.68 1.32 2.4C2.2 1.12 3.6 0.32 5.52 0L6 1.68C4.8 2 3.96 2.48 3.48 3.12C3 3.76 2.76 4.56 2.76 5.52H5.76V12H0ZM8.24 12V7.2C8.24 5.28 8.68 3.68 9.56 2.4C10.44 1.12 11.84 0.32 13.76 0L14.24 1.68C13.04 2 12.2 2.48 11.72 3.12C11.24 3.76 11 4.56 11 5.52H14V12H8.24Z"
        fill="#8058C4"
      />
    </svg>
  );
}

function PencilMark() {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 256 256"
      fill="#8058C4"
      className="shrink-0"
      aria-hidden
    >
      <path d="M227.31 73.37 182.63 28.68a16 16 0 0 0-22.63 0L36.69 152A15.86 15.86 0 0 0 32 163.31V208a16 16 0 0 0 16 16h44.69a15.86 15.86 0 0 0 11.31-4.69L227.31 96a16 16 0 0 0 0-22.63ZM92.69 208H48v-44.69l88-88L180.69 120ZM192 108.68 147.31 64l24-24L216 84.68Z" />
    </svg>
  );
}

function AnonymousHome() {
  const navigate = useNavigate();

  return (
    <div className="px-5 pt-5 pb-10">
      <Logo className="h-14" />

      <div
        role="button"
        tabIndex={0}
        onClick={() => navigate("/signup")}
        onKeyDown={(e) => {
          // 네이티브 버튼은 Enter 와 Space 둘 다 눌린다. role="button" 도 같아야 한다.
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault(); // Space 의 기본 동작(스크롤) 방지
            navigate("/signup");
          }
        }}
        className="mt-5 w-full cursor-pointer overflow-hidden rounded-2xl transition-transform active:scale-[0.99]"
      >
        <img
          src={homeHeroCard}
          alt="함께 만드는 작은 습관 - 함께 힘을 모으면 꾸준해져요 - 꼬밋 시작하기"
          className="w-full object-contain pixelated"
        />
      </div>

      <section className="mt-7">
        <h3 className="text-[16px] font-semibold text-gray-900">
          꼬밋에서 만나는 변화
        </h3>

        <div className="mt-3 w-full overflow-hidden rounded-2xl">
          <img
            src={homeChangesCards}
            alt="오늘을 인증하고, 기록을 차곡차곡 키우고, 한 달을 함께 완성해요"
            className="w-full object-contain pixelated"
          />
        </div>
      </section>
    </div>
  );
}

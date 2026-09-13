import {
  CalendarBlankIcon,
  CheckSquareIcon,
  CrownSimpleIcon,
  DropIcon,
} from "@phosphor-icons/react";
import type {
  ItemSlot,
  MergeParticipantResponse,
} from "../../../shared/api/types";
import { CharacterView } from "../../item/components/CharacterView";

/** LocalDate("YYYY-MM-DD")를 "YYYY.MM.DD"로. */
function formatDateDot(localDate: string): string {
  return localDate.replaceAll("-", ".");
}

/**
 * 머지 결과 카드. 머지 상세 화면의 윗부분이자, 챌린지 현황의 아카이브 미리보기다.
 *
 * 두 화면이 같은 그림을 써야 해서 떼어 뒀다. 미리보기는 작게 들어가므로 바깥에서
 * 크기를 줄여 쓴다.
 */
export function MergeSummaryCard({
  groupName,
  badgeLabel,
  periodStart,
  periodEnd,
  totalDays,
  totalCheckInCount,
  averageCompletionRate,
  participants,
  characters,
  summaryDescription,
  showDaysTogetherStat = true,
}: {
  groupName: string;
  badgeLabel: string;
  periodStart: string;
  periodEnd: string;
  totalDays: number;
  totalCheckInCount: number;
  averageCompletionRate: number;
  participants: MergeParticipantResponse[];
  characters: Record<number, Partial<Record<ItemSlot, string>>>;
  summaryDescription: string;
  showDaysTogetherStat?: boolean;
}) {
  const sorted = [...participants].sort((a, b) => a.ranking - b.ranking);

  return (
    <section className="rounded-2xl border border-purple-200 bg-white px-5 py-6">
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-bold tracking-wide text-purple-600">
          {badgeLabel}
        </span>
        <span className="text-[12px] text-gray-500">
          {formatDateDot(periodStart)} - {formatDateDot(periodEnd)}
        </span>
      </div>

      <div className="mt-4 flex items-center gap-2">
        <PixelDots />
        <div className="min-w-0 flex-1">
          <p
            className="truncate text-center text-[15px] font-bold text-purple-500"
            style={{ fontFamily: "'NeoDunggeunmo', monospace" }}
          >
            {groupName}
          </p>
          <div className="mt-2 h-px bg-purple-200" />
        </div>
        <PixelDots />
      </div>

      <div className="mt-5 flex items-center justify-center">
        <span
          className="rounded-none border-2 border-purple-500 px-2.5 py-1 text-[26px] whitespace-nowrap text-purple-600 shadow-[3px_3px_0_0_#b9a2e0]"
          style={{ fontFamily: "'Press Start 2P', monospace" }}
        >
          MERGED
        </span>
      </div>
      <p className="mt-3 text-center text-[13px] text-gray-500">
        {summaryDescription}
      </p>

      <ul className="mt-6 flex justify-center gap-1.5">
        {sorted.map((p, index) => (
          <li
            key={p.userId}
            className="flex min-w-0 flex-1 flex-col items-center"
          >
            <div className="relative w-full max-w-12">
              {index === 0 && (
                <CrownSimpleIcon
                  size={16}
                  weight="fill"
                  className="absolute -top-4 left-1/2 -translate-x-1/2 text-amber-400"
                  aria-hidden
                />
              )}
              <div className="aspect-square w-full">
                <CharacterView
                  art={characters[p.userId] ?? {}}
                  label={`${p.nickname} 캐릭터`}
                  fit="tight"
                />
              </div>
            </div>
            <p className="mt-1.5 w-full truncate text-center text-[11px] font-medium text-gray-700">
              {p.nickname}
            </p>
            <p className="text-[12px] font-bold text-purple-600">
              {p.completionRate}%
            </p>
          </li>
        ))}
      </ul>

      <div className="mt-6 flex items-center justify-around border-t border-purple-100 pt-4 text-[12px] text-gray-500">
        {showDaysTogetherStat && (
          <SummaryStat icon={<CalendarBlankIcon size={16} weight="bold" />}>
            <span className="font-bold text-purple-600">{totalDays}일</span>{" "}
            함께함
          </SummaryStat>
        )}
        <SummaryStat icon={<CheckSquareIcon size={16} weight="bold" />}>
          총{" "}
          <span className="font-bold text-purple-600">
            {totalCheckInCount}회
          </span>{" "}
          인증
        </SummaryStat>
        <SummaryStat icon={<DropIcon size={16} weight="bold" />}>
          평균 완주율{" "}
          <span className="font-bold text-purple-600">
            {averageCompletionRate}%
          </span>
        </SummaryStat>
      </div>
    </section>
  );
}

function SummaryStat({
  icon,
  children,
}: {
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <p className="flex items-center gap-1.5">
      <span className="text-purple-400">{icon}</span>
      <span>{children}</span>
    </p>
  );
}

/** 카드 제목 양옆의 픽셀 장식. */
function PixelDots() {
  return (
    <div className="grid shrink-0 grid-cols-2 gap-1" aria-hidden>
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
      <span className="h-1 w-1 bg-purple-300" />
    </div>
  );
}

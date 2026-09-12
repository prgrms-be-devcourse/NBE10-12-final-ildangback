import { CaretRightIcon } from "@phosphor-icons/react";
import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import type {
  ChallengeMergeOverviewResponse,
  ItemSlot,
  MonthlyMergeDetailResponse,
} from "../../../shared/api/types";
import { getCharacters } from "../../item/api";
import { toCharacterArt } from "../../item/lib/shop";
import {
  getChallengeMergeOverview,
  getMergeList,
  getMonthlyMergeDetail,
} from "../api";
import { MergeSummaryCard } from "./MergeSummaryCard";

const ORDINAL = [
  "",
  "첫 번째",
  "두 번째",
  "세 번째",
  "네 번째",
  "다섯 번째",
  "여섯 번째",
  "일곱 번째",
  "여덟 번째",
  "아홉 번째",
  "열 번째",
];

function ordinalLabel(seqNo: number): string {
  return ORDINAL[seqNo] ?? `${seqNo}번째`;
}

/** 챌린지 시작일 기준 며칠째인지. 시안의 "Day 61 - Day 90" 표기다. */
function dayOffset(start: string, date: string): number {
  const ms =
    new Date(`${date}T00:00:00`).getTime() -
    new Date(`${start}T00:00:00`).getTime();
  return Math.floor(ms / 86_400_000) + 1;
}

/**
 * 챌린지 현황의 월간 머지 아카이브. 가장 최근 회차 하나만 머지 결과 카드로
 * 보여주고 나머지는 전체보기로 넘긴다. 아래에 진행 중인 회차의 진행률이 붙는다.
 *
 * 카드에 참여자 캐릭터가 들어가서 목록이 아니라 상세를 부른다 - 목록 응답에는
 * 참여자가 없다.
 */
export function MergeArchiveSection({ challengeId }: { challengeId: number }) {
  const navigate = useNavigate();
  const [overview, setOverview] =
    useState<ChallengeMergeOverviewResponse | null>(null);
  const [latest, setLatest] = useState<MonthlyMergeDetailResponse | null>(null);
  const [characters, setCharacters] = useState<
    Record<number, Partial<Record<ItemSlot, string>>>
  >({});
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      try {
        const [summary, page] = await Promise.all([
          getChallengeMergeOverview(challengeId),
          getMergeList(challengeId, { size: 1 }),
        ]);
        if (cancelled) return;
        setOverview(summary);

        const head = page.content[0];
        if (head && head.type === "MONTHLY" && head.seqNo !== null) {
          const detail = await getMonthlyMergeDetail(challengeId, head.seqNo);
          if (cancelled) return;
          setLatest(detail);

          const art = await getCharacters(
            detail.participants.map((p) => p.userId),
          );
          if (cancelled) return;
          setCharacters(
            Object.fromEntries(
              Object.entries(art).map(([id, slots]) => [
                Number(id),
                toCharacterArt(slots),
              ]),
            ),
          );
        }
      } catch {
        // 머지를 못 받아도 현황 화면의 나머지는 보여준다.
      } finally {
        if (!cancelled) setReady(true);
      }
    }

    run();
    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  if (!ready) return null;

  // 진행 중인 회차는 아직 발행 전이라 목록에 없다. 개요가 알려준다.
  const inProgress =
    overview && overview.currentSeqNo !== null && overview.currentCycleDay
      ? {
          seqNo: overview.currentSeqNo,
          day: overview.currentCycleDay,
          remain: overview.cycleLengthDays - overview.currentCycleDay,
          rate: Math.min(
            100,
            Math.round(
              (overview.currentCycleDay / overview.cycleLengthDays) * 100,
            ),
          ),
        }
      : null;

  if (!latest && !inProgress) return null;

  return (
    <section className="rounded-2xl border border-purple-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-[14px] font-bold text-gray-900">
          월간 머지 아카이브
        </h3>
        <button
          type="button"
          onClick={() => navigate(`/challenges/${challengeId}/merges`)}
          className="flex items-center gap-0.5 rounded text-[12px] font-semibold text-purple-600 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
        >
          전체보기
          <CaretRightIcon size={12} weight="bold" aria-hidden />
        </button>
      </div>

      {/* 왼쪽 세로선이 회차를 잇는다. 발행된 회차는 채운 점, 진행 중은 빈 점. */}
      <div className="relative mt-3 pl-5">
        <span
          className="absolute top-2 bottom-2 left-[5px] w-px bg-purple-200"
          aria-hidden
        />

        {latest && overview && (
          <div className="relative">
            <span
              className="absolute top-4 -left-5 size-[11px] rounded-full border-2 border-purple-500 bg-purple-500"
              aria-hidden
            />
            <button
              type="button"
              onClick={() =>
                navigate(
                  `/challenges/${challengeId}/monthly-merges/${latest.seqNo}`,
                )
              }
              className="block w-full rounded-2xl text-left focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
            >
              <p className="mb-1.5 flex items-center gap-1.5 text-[11px] font-semibold text-purple-600">
                <span className="size-1.5 rounded-full bg-purple-500" />
                MERGED
                <span className="text-gray-400">
                  · MERGE #{String(latest.seqNo).padStart(2, "0")}
                </span>
              </p>
              <p className="mb-2 text-[15px] font-bold text-gray-900">
                {ordinalLabel(latest.seqNo)} 월간 머지
                <span className="ml-2 text-[12px] font-semibold text-purple-600">
                  Day {dayOffset(overview.periodStart, latest.periodStart)} -
                  Day {dayOffset(overview.periodStart, latest.periodEnd)}
                </span>
              </p>

              <MergeSummaryCard
                groupName={overview.groupName}
                badgeLabel={`MONTHLY MERGE #${String(latest.seqNo).padStart(2, "0")}`}
                periodStart={latest.periodStart}
                periodEnd={latest.periodEnd}
                totalDays={latest.totalDays}
                totalCheckInCount={latest.totalCheckInCount}
                averageCompletionRate={latest.averageCompletionRate}
                participants={latest.participants}
                characters={characters}
                summaryDescription={`${latest.totalDays}일의 기록을 하나로 합쳤어요`}
              />
            </button>
          </div>
        )}

        {inProgress && (
          <div className="relative mt-3">
            <span
              className="absolute top-4 -left-5 size-[11px] rounded-full border-2 border-purple-300 bg-white"
              aria-hidden
            />
            <div className="rounded-xl bg-gray-50 px-3 py-2.5">
              <div className="flex items-center justify-between gap-2">
                <span className="min-w-0 text-[11px]">
                  <span className="font-semibold text-purple-600">
                    MERGED #{String(inProgress.seqNo).padStart(2, "0")}
                  </span>{" "}
                  <span className="font-semibold text-gray-700">진행 중</span>
                  <span className="mt-0.5 block text-gray-500">
                    {inProgress.day}일째 · 머지까지 D-{inProgress.remain}
                  </span>
                </span>
                <span className="shrink-0 text-[13px] font-bold text-purple-600 tabular-nums">
                  {inProgress.rate}%
                </span>
              </div>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-gray-200">
                <div
                  className="h-full rounded-full bg-purple-500"
                  style={{ width: `${inProgress.rate}%` }}
                />
              </div>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}

import { NotePencilIcon } from "@phosphor-icons/react";
import { useState } from "react";
import { formatMonthDayWeekday } from "../../../shared/lib/date";
import { Button } from "../../../shared/ui/Button";
import { currentMonth } from "../lib/month";
import { useDailyLogs } from "../lib/useDailyLogs";
import type { DailyLog } from "../types";
import { DailyLogPlayer } from "./DailyLogPlayer";
import { DailyLogTile } from "./DailyLogTile";
import { LoadMore } from "./LoadMore";
import { MonthNav } from "./MonthNav";

/**
 * 챌린지 상세 > 일일 로그 탭. 시안: front/docs/checkin-gallery-wireframe/일일로그.png
 *
 * 월 네비 + "이번 달 N일 기록·평균 X%" 배너 + 타임라인(하루 = 타일 1개).
 * daily-log API 는 후속 PR — 지금은 dev 스텁, videoUrl 이 없어 타일은 placeholder 다.
 */
export function DailyLogTimeline({ challengeId }: { challengeId: number }) {
  const [month, setMonth] = useState(currentMonth);
  const [playing, setPlaying] = useState<DailyLog | null>(null);

  const {
    items,
    meta,
    loading,
    loadingMore,
    error,
    hasNext,
    loadMore,
    reload,
  } = useDailyLogs(challengeId, month);

  return (
    <div className="px-4 pt-4 pb-10">
      <MonthNav month={month} onChange={setMonth} />

      {meta && (
        <div className="mt-3 flex items-center gap-2 rounded-xl bg-purple-50 px-4 py-2.5 text-[13px] text-gray-600">
          <NotePencilIcon size={16} weight="fill" className="text-purple-400" />
          이번 달{" "}
          <b className="font-bold text-purple-500">{meta.recordDays}일</b> 기록
          · 평균 <b className="font-bold text-purple-500">{meta.avgRate}%</b>
        </div>
      )}

      <div className="mt-5">
        {loading ? (
          <TimelineSkeleton />
        ) : error ? (
          <div className="py-16 text-center">
            <p className="text-[14px] text-gray-500">불러오지 못했어요</p>
            <Button variant="secondary" className="mt-4" onClick={reload}>
              다시 시도
            </Button>
          </div>
        ) : items.length === 0 ? (
          <p className="py-16 text-center text-[14px] text-gray-400">
            이번 달 일일 로그가 없어요
          </p>
        ) : (
          <ol className="relative border-l-2 border-purple-100 pl-5">
            {items.map((log) => (
              <li key={log.id} className="relative mb-6">
                <span className="absolute top-1 -left-[26px] size-3 rounded-full border-2 border-purple-400 bg-white" />
                <h3 className="text-[13px] font-bold text-gray-900">
                  {formatMonthDayWeekday(log.businessDate)}
                </h3>
                <div className="mt-2">
                  <DailyLogTile log={log} onPlay={() => setPlaying(log)} />
                </div>
              </li>
            ))}
            <LoadMore
              hasNext={hasNext}
              loadingMore={loadingMore}
              onLoadMore={loadMore}
            />
          </ol>
        )}
      </div>

      {playing?.videoUrl && (
        <DailyLogPlayer
          src={playing.videoUrl}
          onClose={() => setPlaying(null)}
        />
      )}
    </div>
  );
}

function TimelineSkeleton() {
  return (
    <div className="space-y-6 pl-5">
      {Array.from({ length: 3 }, (_, i) => (
        <div key={i}>
          <div className="h-3 w-20 animate-pulse rounded bg-gray-100" />
          <div className="mt-2 aspect-[3/2] w-full animate-pulse rounded-lg bg-gray-100" />
        </div>
      ))}
    </div>
  );
}

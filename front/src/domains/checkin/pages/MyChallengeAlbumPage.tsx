import { useState } from "react";
import { useParams } from "react-router";
import { TopBar } from "../../../shared/ui/TopBar";
import { CheckInGridSection } from "../components/CheckInGridSection";
import { CheckInLightbox } from "../components/CheckInLightbox";
import { CheckInTypeChips } from "../components/CheckInTypeChips";
import { MonthNav } from "../components/MonthNav";
import { currentMonth } from "../lib/month";
import { useChallengeAlbumSummary } from "../lib/useChallengeAlbumSummary";
import { useMyCheckIns } from "../lib/useMyCheckIns";
import type { CheckInType, MyCheckIn } from "../types";

/**
 * 프로필 > 참여했던 챌린지 > 그룹 앨범. `GET /users/me/check-ins?challengeId=`.
 * 시안: front/docs/checkin-profile-wireframe/프로필 - 그룹 앨범.png
 */
export function MyChallengeAlbumPage() {
  const challengeId = Number(useParams().challengeId);
  const [month, setMonth] = useState(currentMonth);
  const [checkInType, setCheckInType] = useState<CheckInType | null>(null);
  const [selected, setSelected] = useState<MyCheckIn | null>(null);

  const summary = useChallengeAlbumSummary(challengeId);
  const result = useMyCheckIns({
    month,
    challengeId,
    checkInType: checkInType ?? undefined,
  });
  const total = result.meta?.totalCount;

  return (
    <>
      <TopBar title={summary ? `${summary.name} 앨범` : "앨범"} />

      <div className="flex-1 px-5 pb-10">
        {summary && (
          <div className="mt-2 rounded-2xl border border-purple-100 px-4 py-3">
            <div className="flex items-center justify-between">
              <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[12px] font-semibold text-purple-500">
                {summary.category}
              </span>
              {summary.active && (
                <span className="flex items-center gap-1 text-[12px] text-gray-400">
                  <span className="size-1.5 rounded-full bg-purple-500" />
                  진행 중
                </span>
              )}
            </div>
            <p className="mt-1 text-[17px] font-bold text-gray-900">
              {summary.name}
            </p>
          </div>
        )}

        <div className="mt-4">
          <MonthNav month={month} onChange={setMonth} />
          {summary && (
            <p className="mt-1 text-center text-[12px] text-gray-400">
              {periodLabel(summary.startDate, summary.endDate)}
              {total != null && `  ·  내 인증 ${total}개`}
            </p>
          )}
        </div>

        <div className="mt-3">
          <CheckInTypeChips value={checkInType} onChange={setCheckInType} />
        </div>

        <div className="mt-3">
          <CheckInGridSection
            result={result}
            groupByDate
            emptyMessage="이번 달 기록이 없어요"
            onSelect={setSelected}
          />
        </div>
      </div>

      <CheckInLightbox item={selected} onClose={() => setSelected(null)} />
    </>
  );
}

/** "2026-08-20", "2027-02-15" → "8.20 - 2027.02.15" (시안 형식) */
function periodLabel(start: string, end: string): string {
  const [, sm, sd] = start.slice(0, 10).split("-");
  const [ey, em, ed] = end.slice(0, 10).split("-");
  return `${Number(sm)}.${Number(sd)} - ${ey}.${em}.${ed}`;
}

import { useState } from "react";
import { TopBar } from "../../../shared/ui/TopBar";
import { CheckInGridSection } from "../components/CheckInGridSection";
import { CheckInLightbox } from "../components/CheckInLightbox";
import { CheckInSelect } from "../components/CheckInSelect";
import { currentMonth, monthLabel, recentMonths } from "../lib/month";
import { useMyCheckIns } from "../lib/useMyCheckIns";
import { useMyChallenges } from "../lib/useMyChallenges";
import type { MyCheckIn } from "../types";

const MONTHS = recentMonths(12);

/**
 * 프로필 > 전체 인증. 시안: front/docs/checkin-profile-wireframe/프로필 - 전체 인증.png
 *
 * `GET /users/me/check-ins` — 챌린지 필터 없이 전체. 월·챌린지 드롭다운, 날짜별 그리드.
 * 본인 것이라 작성자 뱃지는 없다.
 */
export function MyCheckInsPage() {
  const [month, setMonth] = useState(currentMonth);
  const [challengeId, setChallengeId] = useState<number | undefined>();
  const [selected, setSelected] = useState<MyCheckIn | null>(null);

  const challenges = useMyChallenges();
  const result = useMyCheckIns({ month, challengeId });
  const total = result.meta?.totalCount;

  return (
    <>
      <TopBar title="전체 인증" />

      <div className="flex-1 px-5 pb-10">
        <div className="mt-2 flex gap-2">
          <CheckInSelect aria-label="월 선택" value={month} onChange={setMonth}>
            {MONTHS.map((m) => (
              <option key={m} value={m}>
                {monthLabel(m)}
              </option>
            ))}
          </CheckInSelect>
          <CheckInSelect
            aria-label="챌린지 선택"
            value={challengeId == null ? "" : String(challengeId)}
            onChange={(v) => setChallengeId(v ? Number(v) : undefined)}
          >
            <option value="">전체 챌린지</option>
            {challenges.map((c) => (
              <option key={c.challengeId} value={c.challengeId}>
                {c.name}
              </option>
            ))}
          </CheckInSelect>
        </div>

        {total != null && (
          <p className="mt-3 border-t border-gray-100 pt-2 text-right text-[12px] text-gray-400">
            총 <span className="font-bold text-purple-500">{total}</span>개의
            기록
          </p>
        )}

        <div className="mt-2">
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

import { CheckCircleIcon, XCircleIcon } from "@phosphor-icons/react";
import type { PersonalStatsData } from "../lib/personalStats";
import { ContributionGrid } from "../../../shared/ui/ContributionGrid";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { designArt } from "../../../shared/ui/designArt";
import { DonutChart } from "./DonutChart";
import {
  BANNER,
  CARD,
  CARD_TITLE,
  DEEP,
  INK,
  LIGHT,
  TRACK,
} from "./statsTokens";

export function SummaryTab({ stats }: { stats: PersonalStatsData }) {
  const attempted = stats.succeeded + stats.missed;
  const keptOutOfTen = Math.round((stats.succeeded / attempted) * 10);

  return (
    <>
      <div className="grid grid-cols-3 gap-[5.7px]">
        <StatTile
          icon={designArt.statTotalVerifications}
          label="총 인증"
          value={stats.totalCheckIns}
          unit="회"
        />
        <StatTile
          icon={designArt.statLongestStreak}
          label="최고 스트릭"
          value={stats.bestStreak}
          unit="일"
        />
        <StatTile
          icon={designArt.statMonthlySuccess}
          label="전체 성공률"
          value={stats.successRate}
          unit="%"
        />
      </div>

      <section className={`mt-[11px] ${CARD} pt-[15px] pb-[10px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>전체 인증 결과</h2>

        <div className="mt-[19px] flex items-start pl-[24px]">
          <DonutChart
            segments={[{ value: stats.succeeded, color: DEEP }]}
            total={attempted}
            size={121}
            thickness={11}
            trackColor={TRACK}
            rounded
            label={`전체 성공률 ${stats.successRate}퍼센트`}
          >
            <span
              className="text-[36px] leading-none font-bold"
              style={{ color: DEEP }}
            >
              {stats.successRate}
              <span className="text-[22px]">%</span>
            </span>
            <span
              className="mt-[6px] text-[13px] font-semibold"
              style={{ color: LIGHT }}
            >
              성공률
            </span>
          </DonutChart>

          <div className="mt-[13px] ml-[19px] flex-1 pr-[20px]">
            <ResultRow tone="done" label="인증 완료" value={stats.succeeded} />
            <div className="mt-[16px]">
              <ResultRow tone="missed" label="미인증" value={stats.missed} />
            </div>

            <div
              className="mt-[19px] h-[7.3px] w-full overflow-hidden rounded-[3.65px]"
              style={{ backgroundColor: TRACK }}
            >
              <div
                className="h-full rounded-[3.65px]"
                style={{
                  width: `${stats.successRate}%`,
                  backgroundColor: DEEP,
                }}
              />
            </div>
          </div>
        </div>

        <p
          className="mx-[10px] mt-[17px] flex h-[32px] items-center justify-center rounded-[6px] text-[12px] font-semibold"
          style={{ backgroundColor: BANNER, color: INK }}
        >
          10번 중&nbsp;
          <span style={{ color: DEEP }}>{keptOutOfTen}번</span>은 놓치지
          않았어요
        </p>
      </section>

      <section className={`mt-[11px] ${CARD} pt-[15px] pb-[6px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>누적 인증 잔디</h2>
        <div className="mt-[8px] px-[12px]">
          <ContributionGrid
            days={stats.grass}
            gap={5}
            cell={12}
            showDayLabels
          />
        </div>
      </section>

      <section className={`mt-[11px] ${CARD} pt-[15px] pb-[15px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>챌린지 완주 현황</h2>

        <div className="mt-[16px] flex justify-center gap-[11px]">
          <OutcomeTile
            done
            label="완주한 챌린지"
            value={stats.completedChallenges}
          />
          <OutcomeTile
            label="진행 중 챌린지"
            value={stats.inProgressChallenges}
          />
        </div>
      </section>
    </>
  );
}

function StatTile({
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
    <div
      className={`flex h-[52px] items-center gap-[7px] rounded-[6.65px] border-[0.7px] border-purple-200 bg-[#FEFEFE] pl-[9px]`}
    >
      <PixelIcon src={icon} size={30} />
      <div>
        <p className="text-[10px] leading-none text-gray-500">{label}</p>
        <p className="mt-[7px] leading-none">
          <span className="text-[20px] font-bold" style={{ color: DEEP }}>
            {value}
          </span>
          <span className="ml-px text-[12px]" style={{ color: LIGHT }}>
            {unit}
          </span>
        </p>
      </div>
    </div>
  );
}

function ResultRow({
  tone,
  label,
  value,
}: {
  tone: "done" | "missed";
  label: string;
  value: number;
}) {
  const done = tone === "done";

  return (
    <div className="flex items-center gap-[9px]">
      {done ? (
        <CheckCircleIcon
          size={26}
          weight="fill"
          style={{ color: DEEP }}
          className="shrink-0"
          aria-hidden
        />
      ) : (
        <XCircleIcon
          size={23}
          weight="fill"
          className="shrink-0 text-gray-300"
          aria-hidden
        />
      )}
      <span className="flex-1 text-[11px] text-gray-900">{label}</span>
      <span
        className="text-[15px] font-bold"
        style={{ color: done ? DEEP : INK }}
      >
        {value}
        <span className="text-[11px]">회</span>
      </span>
    </div>
  );
}

function OutcomeTile({
  done = false,
  label,
  value,
}: {
  done?: boolean;
  label: string;
  value: number;
}) {
  return (
    <div
      className="flex h-[59px] w-[160px] items-center gap-[10px] rounded-[6px] pl-[12px]"
      style={{ backgroundColor: done ? BANNER : "#EFEEEE" }}
    >
      {done ? (
        <CheckCircleIcon
          size={43}
          weight="fill"
          style={{ color: DEEP }}
          className="shrink-0"
          aria-hidden
        />
      ) : (
        <XCircleIcon
          size={37}
          weight="fill"
          className="shrink-0 text-gray-300"
          aria-hidden
        />
      )}
      <div>
        <p className="text-[11px] leading-none text-gray-900">{label}</p>
        <p
          className="mt-[8px] text-[25px] leading-none font-bold"
          style={{ color: done ? DEEP : INK }}
        >
          {value}
          <span className="text-[12px]">개</span>
        </p>
      </div>
    </div>
  );
}

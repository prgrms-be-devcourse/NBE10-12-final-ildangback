import { CalendarCheckIcon, CalendarXIcon } from "@phosphor-icons/react";
import type { MonthlyPoint, PersonalStatsData } from "../lib/personalStats";
import { CARD, CARD_TITLE, DEEP, INK, LIGHT, TRACK } from "./statsTokens";

// 눈금 후보. 실제 값을 담을 수 있는 가장 작은 것을 고른다.
// 값을 고정해 두면 목 데이터(최대 56)에서는 멀쩡하다가 실 데이터가 넘길 때 잘린다.
const AXIS_STEPS = [5, 10, 15, 20, 25, 30, 40, 50, 75, 100, 150, 200];

// 시안 눈금은 0/20/40/60 넷이다. 값이 커지면 간격만 벌어진다.
function barAxisMax(values: number[]): number {
  const peak = Math.max(0, ...values);
  const step = AXIS_STEPS.find((s) => s * 3 >= peak) ?? Math.ceil(peak / 3);
  return step * 3;
}

// 시안 눈금은 50~100 이다. 성공률이 그보다 낮으면 바닥을 내린다.
function rateAxisMin(values: number[]): number {
  const low = Math.min(100, ...values);
  return Math.min(50, Math.floor(low / 10) * 10);
}

// 시안의 차트 이미지 자리 그대로다. 막대 높이는 이 칸 안에서만 계산한다.
const BAR_AREA = 132;
const LINE_AREA = 110;
// 막대 위 숫자가 앉을 자리. 막대 칸 위에 따로 둔다.
const BAR_LABEL = 18;
const BAR_WIDTH = 22;

export function MonthlyTab({ stats }: { stats: PersonalStatsData }) {
  const monthly = stats.monthly;

  if (monthly.length === 0) {
    return (
      <p className="py-20 text-center text-[13px] text-gray-500">
        이 기간에는 쌓인 기록이 없어요.
      </p>
    );
  }

  const average = Math.round(
    monthly.reduce((sum, m) => sum + m.checkIns, 0) / monthly.length,
  );
  // 달이 하나뿐이면 비교할 지난달이 없다.
  const diff =
    monthly.length >= 2
      ? monthly[monthly.length - 1].checkIns -
        monthly[monthly.length - 2].checkIns
      : null;
  const barMax = barAxisMax(monthly.map((m) => m.checkIns));
  const barTicks = [barMax, (barMax / 3) * 2, barMax / 3, 0];

  return (
    <>
      <section className={`mt-[4px] ${CARD} pt-[15px] pb-[16px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>월별 인증 횟수</h2>

        <div className="mt-[22px] flex gap-[8px] pr-[16px] pl-[12px]">
          <div
            className="flex w-[16px] shrink-0 flex-col justify-between text-[10px] leading-none text-gray-500"
            style={{ height: BAR_AREA, marginTop: BAR_LABEL }}
          >
            {barTicks.map((tick) => (
              <span key={tick}>{tick}</span>
            ))}
          </div>

          <div className="relative min-w-0 flex-1">
            {barTicks.map((tick) => (
              <span
                key={tick}
                className="absolute inset-x-0 border-t border-dashed border-purple-200"
                style={{ top: BAR_LABEL + BAR_AREA * (1 - tick / barMax) }}
                aria-hidden
              />
            ))}

            <ul className="grid grid-cols-6 gap-[10px]">
              {monthly.map((month, index) => {
                const last = index === monthly.length - 1;
                const height = (month.checkIns / barMax) * BAR_AREA;
                return (
                  <li key={month.month} className="flex flex-col items-center">
                    <span
                      className="relative w-full"
                      style={{ height: BAR_AREA + BAR_LABEL }}
                    >
                      <span
                        className="absolute bottom-0 left-1/2 -translate-x-1/2 rounded-t-[4px]"
                        style={{
                          width: BAR_WIDTH,
                          height,
                          backgroundColor: last ? DEEP : LIGHT,
                        }}
                      />
                      <span
                        className="absolute left-1/2 -translate-x-1/2 text-[11px] leading-none font-bold"
                        style={{ bottom: height + 5, color: DEEP }}
                      >
                        {month.checkIns}
                      </span>
                    </span>
                    <span className="mt-[7px] text-[10px] leading-none text-gray-500">
                      {month.month}월
                    </span>
                  </li>
                );
              })}
            </ul>
          </div>
        </div>

        <div className="mt-[18px] flex items-baseline justify-center gap-[34px] text-[11px] text-gray-500">
          <p>
            월 평균{" "}
            <span className="text-[22px] font-bold" style={{ color: DEEP }}>
              {average}회
            </span>
          </p>
          {diff !== null && (
            <p>
              지난달보다{" "}
              <span className="text-[22px] font-bold" style={{ color: DEEP }}>
                {diff > 0 ? "+" : ""}
                {diff}회
              </span>
            </p>
          )}
        </div>
      </section>

      <section className={`mt-[15px] ${CARD} pt-[15px] pb-[16px]`}>
        <h2 className={`${CARD_TITLE} pl-[12px]`}>월별 성공률</h2>
        <SuccessRateLine monthly={monthly} />
      </section>

      <div className="mt-[15px] flex gap-[11px]">
        <HighlightCard
          tone="best"
          label="가장 부지런한 달"
          headline={`${stats.bestMonth}월`}
          detail={`${stats.bestMonthCheckIns}회`}
        />
        <HighlightCard
          tone="worst"
          label="가장 많이 놓친 달"
          headline={`${stats.worstMonth}월`}
          detail={`완주율 ${stats.worstMonthRate}%`}
        />
      </div>
    </>
  );
}

function SuccessRateLine({ monthly }: { monthly: MonthlyPoint[] }) {
  const rateMin = rateAxisMin(monthly.map((m) => m.successRate));
  // 눈금은 rateMin 부터 100 까지다. 양 끝을 4% 씩 밀어 넣어야
  // 첫 점과 끝 점의 퍼센트 라벨이 카드 밖으로 새지 않는다.
  const points = monthly.map((month, index) => ({
    month: month.month,
    rate: month.successRate,
    x: monthly.length > 1 ? 4 + (index / (monthly.length - 1)) * 92 : 50,
    y: ((100 - month.successRate) / (100 - rateMin)) * 100,
  }));

  return (
    <div className="mt-[22px] flex gap-[8px] pr-[16px] pl-[12px]">
      <div
        className="flex w-[26px] shrink-0 flex-col justify-between text-[10px] leading-none text-gray-500"
        style={{ height: LINE_AREA }}
      >
        <span>100%</span>
        <span>{rateMin}%</span>
      </div>

      <div className="min-w-0 flex-1">
        <div className="relative" style={{ height: LINE_AREA }}>
          <svg
            className="absolute inset-0 h-full w-full"
            viewBox="0 0 100 100"
            preserveAspectRatio="none"
            aria-hidden
          >
            <defs>
              <linearGradient id="rateFade" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={LIGHT} stopOpacity="0.45" />
                <stop offset="100%" stopColor={LIGHT} stopOpacity="0" />
              </linearGradient>
            </defs>
            <polygon
              points={`${points[0].x},100 ${points.map((p) => `${p.x},${p.y}`).join(" ")} ${points[points.length - 1].x},100`}
              fill="url(#rateFade)"
            />
            <polyline
              points={points.map((p) => `${p.x},${p.y}`).join(" ")}
              fill="none"
              stroke={LIGHT}
              strokeWidth={2}
              strokeLinejoin="round"
              vectorEffect="non-scaling-stroke"
            />
          </svg>

          {points.map((point, index) => {
            const last = index === points.length - 1;
            return (
              <div
                key={point.month}
                className="absolute -translate-x-1/2 -translate-y-1/2"
                style={{ left: `${point.x}%`, top: `${point.y}%` }}
              >
                <span
                  className="absolute bottom-[10px] left-1/2 -translate-x-1/2 text-[10px] font-bold whitespace-nowrap"
                  style={{ color: last ? DEEP : "#6B6478" }}
                >
                  {point.rate}%
                </span>
                <span
                  className="block h-[9px] w-[9px] rounded-full border-2 bg-white"
                  style={{ borderColor: last ? DEEP : LIGHT }}
                />
              </div>
            );
          })}
        </div>

        <ul
          className="mt-[7px] grid text-center text-[10px] leading-none text-gray-500"
          style={{ gridTemplateColumns: `repeat(${points.length}, 1fr)` }}
        >
          {points.map((point) => (
            <li key={point.month}>{point.month}월</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

function HighlightCard({
  tone,
  label,
  headline,
  detail,
}: {
  tone: "best" | "worst";
  label: string;
  headline: string;
  detail: string;
}) {
  const best = tone === "best";

  return (
    <div
      className="flex h-[69px] flex-1 items-center gap-[11px] rounded-[6.65px] border-[0.7px] pl-[12px]"
      style={{ borderColor: best ? "#D9CDED" : "#939393" }}
    >
      <span
        className="flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-full"
        style={{ backgroundColor: best ? TRACK : "#EFEEEE" }}
      >
        {best ? (
          <CalendarCheckIcon size={24} weight="fill" style={{ color: DEEP }} />
        ) : (
          <CalendarXIcon size={24} weight="fill" className="text-gray-400" />
        )}
      </span>
      <div className="min-w-0">
        <p className="text-[10px] leading-none text-gray-500">{label}</p>
        <p
          className="mt-[7px] text-[18px] leading-none font-bold"
          style={{ color: INK }}
        >
          {headline}
        </p>
        <p
          className="mt-[6px] text-[12px] leading-none font-bold"
          style={{ color: DEEP }}
        >
          {detail}
        </p>
      </div>
    </div>
  );
}

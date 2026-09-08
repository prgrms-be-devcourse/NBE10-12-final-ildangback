import { CalendarBlankIcon, CheckIcon, XIcon } from "@phosphor-icons/react";
import { useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from "recharts";
import { getMyStats } from "../api";
import { CATEGORY_LABEL } from "../lib/category";
import { TopBar } from "../../../shared/ui/TopBar";
import type { PersonalStatsResponse } from "../../../shared/api/types";

/** "2026-08" -> "8월". */
function formatMonthLabel(yyyyMM: string): string {
  return `${Number(yyyyMM.slice(5, 7))}월`;
}

const HEATMAP_COLORS = ["#EEEEEE", "#D9CDED", "#B9A2E0", "#8551C9", "#5A3E89"];

const TABS = ["요약", "월별", "카테고리", "패턴"] as const;
type Tab = (typeof TABS)[number];

export function PersonalStatsPage() {
  const [tab, setTab] = useState<Tab>("요약");
  const [stats, setStats] = useState<PersonalStatsResponse | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let cancelled = false;

    getMyStats()
      .then((data) => {
        if (!cancelled) setStats(data);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <>
      <TopBar title="개인 통계" />

      <div className="px-5 pt-3 pb-10">
        <div className="flex rounded-full bg-gray-100 p-1">
          {TABS.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTab(t)}
              className={`flex-1 rounded-full py-2 text-[13px] font-semibold transition-colors ${
                tab === t
                  ? "bg-purple-500 text-white"
                  : "text-gray-500 hover:text-gray-700"
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        {/* 기간 필터는 아직 백엔드가 지원 안 해서 표시만 하고 동작은 안 한다. */}
        <div className="mt-4 flex w-fit items-center gap-1.5 rounded-lg border border-purple-200 bg-white px-3 py-2 text-[13px] font-medium text-gray-600">
          <CalendarBlankIcon size={16} className="text-purple-400" />
          전체 기간
        </div>

        {error && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            통계를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}

        {!error && !stats && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            불러오는 중…
          </p>
        )}

        {stats &&
          stats.summary.completedChallengeCount +
            stats.summary.inProgressChallengeCount ===
            0 && (
            <p className="py-10 text-center text-[13px] text-gray-500">
              아직 발행된 머지 기록이 없어요. 챌린지가 진행되면 여기 통계가
              쌓여요.
            </p>
          )}

        {stats &&
          stats.summary.completedChallengeCount +
            stats.summary.inProgressChallengeCount >
            0 && (
            <div className="mt-4">
              {tab === "요약" && <SummaryTab stats={stats} />}
              {tab === "월별" && <MonthlyTab stats={stats} />}
              {tab === "카테고리" && <CategoryTab stats={stats} />}
              {tab === "패턴" && <PatternTab />}
            </div>
          )}
      </div>
    </>
  );
}

function SummaryTab({ stats }: { stats: PersonalStatsResponse }) {
  const { summary, heatmap } = stats;
  const totalDayCount = summary.completedDayCount + summary.missedDayCount;
  const successOutOfTen = Math.round(summary.averageCompletionRate / 10);

  const donutData = [
    { name: "인증 완료", value: summary.completedDayCount },
    { name: "미인증", value: summary.missedDayCount },
  ];

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-2">
        <StatTile
          icon="🏆"
          label="총 인증"
          value={`${summary.totalCheckInCount}회`}
        />
        <StatTile
          icon="🔥"
          label="최고 스트릭"
          value={`${summary.bestStreakEver}일`}
        />
        <StatTile
          icon="🎯"
          label="전체 성공률"
          value={`${summary.averageCompletionRate}%`}
        />
      </div>

      <section className="rounded-2xl border border-purple-200 bg-white p-5">
        <h2 className="text-[15px] font-bold text-gray-900">전체 인증 결과</h2>
        <div className="mt-4 flex items-center gap-5">
          <div className="relative h-32 w-32 shrink-0">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={donutData}
                  dataKey="value"
                  innerRadius="70%"
                  outerRadius="100%"
                  startAngle={90}
                  endAngle={-270}
                  stroke="none"
                >
                  <Cell fill="#8551C9" />
                  <Cell fill="#EEEEEE" />
                </Pie>
              </PieChart>
            </ResponsiveContainer>
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <p className="text-[20px] font-extrabold text-purple-600">
                {summary.averageCompletionRate}%
              </p>
              <p className="text-[11px] text-gray-500">성공률</p>
            </div>
          </div>
          <div className="flex-1 space-y-2">
            <LegendRow
              color="#8551C9"
              icon={
                <CheckIcon size={12} weight="bold" className="text-white" />
              }
              label="인증 완료"
              value={`${summary.completedDayCount}회`}
            />
            <LegendRow
              color="#EEEEEE"
              icon={<XIcon size={12} weight="bold" className="text-gray-400" />}
              label="미인증"
              value={`${summary.missedDayCount}회`}
            />
          </div>
        </div>
        <div className="mt-4 h-2 overflow-hidden rounded-full bg-purple-50">
          <div
            className="h-full rounded-full bg-purple-500"
            style={{
              width: `${totalDayCount === 0 ? 0 : Math.round((summary.completedDayCount / totalDayCount) * 100)}%`,
            }}
          />
        </div>
        <p className="mt-3 rounded-xl bg-purple-50 py-2.5 text-center text-[13px] text-gray-600">
          10번 중{" "}
          <span className="font-bold text-purple-600">{successOutOfTen}</span>
          번은 놓치지 않았어요
        </p>
      </section>

      {heatmap.length > 0 && (
        <section className="rounded-2xl border border-purple-200 bg-white p-5">
          <h2 className="text-[15px] font-bold text-gray-900">
            누적 인증 잔디
          </h2>
          <div className="mt-4 flex flex-wrap gap-1.5">
            {heatmap.map((cell) => (
              <div
                key={cell.month}
                className="flex flex-col items-center gap-1"
              >
                <div
                  className="h-6 w-6 rounded-md"
                  style={{ backgroundColor: HEATMAP_COLORS[cell.level] }}
                />
                <span className="text-[9px] text-gray-400">
                  {formatMonthLabel(cell.month)}
                </span>
              </div>
            ))}
          </div>
          <div className="mt-3 flex items-center justify-end gap-1.5 text-[10px] text-gray-400">
            적음
            {HEATMAP_COLORS.map((color) => (
              <span
                key={color}
                className="h-2.5 w-2.5 rounded-sm"
                style={{ backgroundColor: color }}
              />
            ))}
            많음
          </div>
        </section>
      )}

      <section className="grid grid-cols-2 gap-3">
        <div className="rounded-2xl border border-purple-200 bg-white p-4 text-center">
          <p className="text-[11px] text-gray-500">완주한 챌린지</p>
          <p className="mt-1 text-[20px] font-extrabold text-gray-900">
            {summary.completedChallengeCount}
            <span className="text-[13px] font-bold text-gray-500">개</span>
          </p>
        </div>
        <div className="rounded-2xl border border-purple-200 bg-white p-4 text-center">
          <p className="text-[11px] text-gray-500">진행 중인 챌린지</p>
          <p className="mt-1 text-[20px] font-extrabold text-gray-900">
            {summary.inProgressChallengeCount}
            <span className="text-[13px] font-bold text-gray-500">개</span>
          </p>
        </div>
      </section>
    </div>
  );
}

function MonthlyTab({ stats }: { stats: PersonalStatsResponse }) {
  const { monthlyTrend } = stats;

  const chartData = useMemo(
    () =>
      monthlyTrend.map((item) => ({
        ...item,
        label: formatMonthLabel(item.month),
      })),
    [monthlyTrend],
  );

  const average = useMemo(() => {
    if (monthlyTrend.length === 0) return 0;
    return Math.round(
      monthlyTrend.reduce((sum, item) => sum + item.checkInCount, 0) /
        monthlyTrend.length,
    );
  }, [monthlyTrend]);

  const deltaFromLastMonth =
    monthlyTrend.length >= 2
      ? monthlyTrend[monthlyTrend.length - 1].checkInCount -
        monthlyTrend[monthlyTrend.length - 2].checkInCount
      : null;

  const steadiestMonth = useMemo(
    () =>
      monthlyTrend.reduce<(typeof monthlyTrend)[number] | null>(
        (best, item) =>
          !best || item.checkInCount > best.checkInCount ? item : best,
        null,
      ),
    [monthlyTrend],
  );

  const worstMonth = useMemo(
    () =>
      monthlyTrend.reduce<(typeof monthlyTrend)[number] | null>(
        (worst, item) =>
          !worst || item.completionRate < worst.completionRate ? item : worst,
        null,
      ),
    [monthlyTrend],
  );

  if (monthlyTrend.length === 0) {
    return (
      <p className="py-10 text-center text-[13px] text-gray-500">
        아직 월별 데이터가 없어요.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-purple-200 bg-white p-5">
        <h2 className="text-[15px] font-bold text-gray-900">월별 인증 횟수</h2>
        <div className="mt-3 h-48">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart
              data={chartData}
              margin={{ top: 16, right: 0, left: -20, bottom: 0 }}
            >
              <CartesianGrid vertical={false} stroke="#F2F0F7" />
              <XAxis
                dataKey="label"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 11, fill: "#9CA3AF" }}
              />
              <YAxis hide />
              <Bar dataKey="checkInCount" radius={[6, 6, 0, 0]} maxBarSize={28}>
                {chartData.map((item, index) => (
                  <Cell
                    key={item.month}
                    fill={
                      index === chartData.length - 1 ? "#8551C9" : "#D9CDED"
                    }
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
        <div className="mt-2 flex items-center justify-between px-1 text-[13px]">
          <p className="text-gray-500">
            월 평균 <span className="font-bold text-gray-900">{average}회</span>
          </p>
          {deltaFromLastMonth != null && (
            <p className="text-gray-500">
              지난달보다{" "}
              <span className="font-bold text-purple-600">
                {deltaFromLastMonth >= 0 ? "+" : ""}
                {deltaFromLastMonth}회
              </span>
            </p>
          )}
        </div>
      </section>

      <section className="rounded-2xl border border-purple-200 bg-white p-5">
        <h2 className="text-[15px] font-bold text-gray-900">월별 성공률</h2>
        <div className="mt-3 h-48">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={chartData}
              margin={{ top: 16, right: 8, left: -20, bottom: 0 }}
            >
              <CartesianGrid vertical={false} stroke="#F2F0F7" />
              <XAxis
                dataKey="label"
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 11, fill: "#9CA3AF" }}
              />
              <YAxis
                domain={[0, 100]}
                tickFormatter={(v: number) => `${v}%`}
                axisLine={false}
                tickLine={false}
                tick={{ fontSize: 11, fill: "#9CA3AF" }}
              />
              <Line
                type="monotone"
                dataKey="completionRate"
                stroke="#8551C9"
                strokeWidth={2}
                dot={{ r: 3, fill: "#8551C9" }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3">
        {steadiestMonth && (
          <div className="rounded-2xl border border-purple-200 bg-white p-4">
            <p className="text-[11px] text-gray-500">가장 꾸준한 달</p>
            <p className="mt-1 text-[18px] font-extrabold text-gray-900">
              {formatMonthLabel(steadiestMonth.month)}
            </p>
            <p className="text-[11px] text-gray-500">
              {steadiestMonth.checkInCount}회
            </p>
          </div>
        )}
        {worstMonth && (
          <div className="rounded-2xl border border-purple-200 bg-white p-4">
            <p className="text-[11px] text-gray-500">가장 많이 놓친 달</p>
            <p className="mt-1 text-[18px] font-extrabold text-gray-900">
              {formatMonthLabel(worstMonth.month)}
            </p>
            <p className="text-[11px] text-gray-500">
              성공률 {worstMonth.completionRate}%
            </p>
          </div>
        )}
      </section>
    </div>
  );
}

const DONUT_COLORS = [
  "#8551C9",
  "#9E75D6",
  "#B497E0",
  "#C9B7E9",
  "#DED8F1",
  "#EDE8F7",
  "#F5F2FA",
];

function CategoryTab({ stats }: { stats: PersonalStatsResponse }) {
  const { categoryBreakdown } = stats;

  const totalMissed = categoryBreakdown.reduce(
    (sum, c) => sum + c.missedDayCount,
    0,
  );

  const best = categoryBreakdown[0] ?? null;
  const worst =
    categoryBreakdown.length > 0
      ? categoryBreakdown[categoryBreakdown.length - 1]
      : null;

  const missedShare = [...categoryBreakdown]
    .sort((a, b) => b.missedDayCount - a.missedDayCount)
    .map((c) => ({
      ...c,
      share:
        totalMissed === 0
          ? 0
          : Math.round((c.missedDayCount / totalMissed) * 100),
    }));

  const top2Share = missedShare
    .slice(0, 2)
    .reduce((sum, c) => sum + c.share, 0);

  if (categoryBreakdown.length === 0) {
    return (
      <p className="py-10 text-center text-[13px] text-gray-500">
        아직 카테고리별 데이터가 없어요.
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-purple-200 bg-white p-5">
        <h2 className="text-[15px] font-bold text-gray-900">
          카테고리별 성공률
        </h2>
        <div className="mt-4 space-y-3">
          {categoryBreakdown.map((c) => (
            <div key={c.category} className="flex items-center gap-3">
              <span className="w-12 shrink-0 text-[12px] font-medium text-gray-600">
                {CATEGORY_LABEL[c.category]}
              </span>
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-gray-100">
                <div
                  className="h-full rounded-full bg-purple-500"
                  style={{ width: `${c.averageCompletionRate}%` }}
                />
              </div>
              <span className="w-10 shrink-0 text-right text-[12px] font-bold text-gray-900">
                {c.averageCompletionRate}%
              </span>
            </div>
          ))}
        </div>
      </section>

      <section className="grid grid-cols-2 gap-3">
        {best && (
          <div className="rounded-2xl border border-green-200 bg-green-50 p-4">
            <p className="text-[12px] font-semibold text-green-700">
              가장 꾸준해요
            </p>
            <p className="mt-1 text-[16px] font-extrabold text-gray-900">
              {CATEGORY_LABEL[best.category]}
            </p>
            <p className="text-[11px] text-gray-500">
              {best.totalCheckInCount}회 인증
            </p>
          </div>
        )}
        {worst && (
          <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4">
            <p className="text-[12px] font-semibold text-amber-700">
              조금 더 챙겨봐요
            </p>
            <p className="mt-1 text-[16px] font-extrabold text-gray-900">
              {CATEGORY_LABEL[worst.category]}
            </p>
            <p className="text-[11px] text-gray-500">
              {worst.totalCheckInCount}회 인증
            </p>
          </div>
        )}
      </section>

      {totalMissed > 0 && (
        <section className="rounded-2xl border border-purple-200 bg-white p-5">
          <h2 className="text-[15px] font-bold text-gray-900">
            놓친 인증 비율
          </h2>
          <div className="mt-3 flex items-center gap-5">
            <div className="h-32 w-32 shrink-0">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={missedShare}
                    dataKey="missedDayCount"
                    innerRadius="55%"
                    outerRadius="100%"
                    stroke="none"
                  >
                    {missedShare.map((c, index) => (
                      <Cell
                        key={c.category}
                        fill={DONUT_COLORS[index % DONUT_COLORS.length]}
                      />
                    ))}
                  </Pie>
                </PieChart>
              </ResponsiveContainer>
            </div>
            <ul className="flex-1 space-y-1.5">
              {missedShare.map((c, index) => (
                <li
                  key={c.category}
                  className="flex items-center justify-between text-[12px]"
                >
                  <span className="flex items-center gap-1.5 text-gray-600">
                    <span
                      className="h-2 w-2 rounded-full"
                      style={{
                        backgroundColor:
                          DONUT_COLORS[index % DONUT_COLORS.length],
                      }}
                    />
                    {CATEGORY_LABEL[c.category]}
                  </span>
                  <span className="font-bold text-gray-900">{c.share}%</span>
                </li>
              ))}
            </ul>
          </div>
          {missedShare.length >= 2 && (
            <p className="mt-4 rounded-xl bg-purple-50 px-4 py-3 text-[13px] text-gray-700">
              <span className="font-bold text-purple-600">
                {CATEGORY_LABEL[missedShare[0].category]}
              </span>
              과{" "}
              <span className="font-bold text-purple-600">
                {CATEGORY_LABEL[missedShare[1].category]}
              </span>{" "}
              인증에서 전체 미인증의{" "}
              <span className="font-bold text-purple-600">{top2Share}%</span>가
              발생했어요
            </p>
          )}
        </section>
      )}
    </div>
  );
}

function PatternTab() {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-purple-200 bg-white py-16">
      <p className="text-[32px]">🚧</p>
      <p className="text-[14px] font-semibold text-gray-700">
        요일별·시간대별 패턴은 준비 중이에요
      </p>
      <p className="px-8 text-center text-[12px] text-gray-400">
        체크인 기록이 언제 쌓였는지 보여주는 기능이라, 인증 시각 데이터 연동
        후에 열릴 예정이에요.
      </p>
    </div>
  );
}

function StatTile({
  icon,
  label,
  value,
}: {
  icon: string;
  label: string;
  value: string;
}) {
  return (
    <div className="rounded-xl border border-purple-200 bg-white p-3 text-center">
      <p className="text-[18px]">{icon}</p>
      <p className="mt-1 text-[11px] text-gray-500">{label}</p>
      <p className="text-[16px] font-extrabold text-gray-900">{value}</p>
    </div>
  );
}

function LegendRow({
  color,
  icon,
  label,
  value,
}: {
  color: string;
  icon: React.ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="flex items-center justify-between">
      <span className="flex items-center gap-2 text-[13px] text-gray-600">
        <span
          className="flex h-4 w-4 items-center justify-center rounded-full"
          style={{ backgroundColor: color }}
        >
          {icon}
        </span>
        {label}
      </span>
      <span className="font-bold text-gray-900">{value}</span>
    </div>
  );
}

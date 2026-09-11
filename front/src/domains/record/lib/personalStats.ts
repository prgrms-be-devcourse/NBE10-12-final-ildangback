// 개인 통계 화면이 쓰는 모양. 서버 응답을 화면 단위로 한 번 접는다.
//
// 응답은 요약, 월별, 카테고리 세 덩이인데 화면은 그것 말고도 파생값을 쓴다 -
// 가장 부지런한 달, 카테고리별 미인증 비율 같은 것들이다. 서버가 주지 않아서
// 여기서 계산한다.

import type {
  CategoryStatResponse,
  GroupCategory,
  PersonalStatsResponse,
} from "../../../shared/api/types";
import type { GrassDay } from "../../../shared/ui/ContributionGrid";

export const STATS_PERIODS = ["ALL", "YEAR", "SIX_MONTHS"] as const;
export type StatsPeriod = (typeof STATS_PERIODS)[number];

export const STATS_PERIOD_LABEL: Record<StatsPeriod, string> = {
  ALL: "전체 기간",
  YEAR: "최근 1년",
  SIX_MONTHS: "최근 6개월",
};

export interface MonthlyPoint {
  /** 1~12. 서버는 "yyyy-MM" 으로 준다. */
  month: number;
  checkIns: number;
  successRate: number;
}

export interface CategoryStat {
  category: GroupCategory;
  successRate: number;
  checkIns: number;
  /** 전체 미인증 중 이 카테고리가 차지하는 비율. 합이 100 이다. */
  missedShare: number;
}

export interface PersonalStatsData {
  totalCheckIns: number;
  bestStreak: number;
  successRate: number;
  succeeded: number;
  missed: number;
  completedChallenges: number;
  /** 최종 머지가 아직 없는 챌린지 수. 서버가 중도 이탈을 몰라서 이것으로 근사한다. */
  inProgressChallenges: number;
  grass: GrassDay[];
  monthly: MonthlyPoint[];
  bestMonth: number;
  bestMonthCheckIns: number;
  worstMonth: number;
  /** 가장 완주율이 낮았던 달의 완주율. 서버가 달별 미인증 횟수를 주지 않는다. */
  worstMonthRate: number;
  categories: CategoryStat[];
}

/** 기간 선택을 서버가 받는 from/to 로 바꾼다. 전체 기간이면 아무것도 안 보낸다. */
export function toDateRange(period: StatsPeriod): {
  from?: string;
  to?: string;
} {
  if (period === "ALL") return {};

  const to = new Date();
  to.setHours(0, 0, 0, 0);

  const from = new Date(to);
  if (period === "YEAR") from.setFullYear(from.getFullYear() - 1);
  else from.setMonth(from.getMonth() - 6);

  return { from: dateKey(from), to: dateKey(to) };
}

function dateKey(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

// "2026-09" 에서 9 를 꺼낸다.
function monthNumber(yearMonth: string): number {
  return Number(yearMonth.slice(5, 7));
}

function toCategoryStats(rows: CategoryStatResponse[]): CategoryStat[] {
  const totalMissed = rows.reduce((sum, row) => sum + row.missedDayCount, 0);

  return rows.map((row) => ({
    category: row.category,
    successRate: row.averageCompletionRate,
    checkIns: row.totalCheckInCount,
    missedShare:
      totalMissed === 0
        ? 0
        : Math.round((row.missedDayCount / totalMissed) * 100),
  }));
}

export function toPersonalStatsData(
  response: PersonalStatsResponse,
  grass: GrassDay[],
): PersonalStatsData {
  const { summary, monthlyTrend, categoryBreakdown } = response;

  const monthly: MonthlyPoint[] = monthlyTrend.map((item) => ({
    month: monthNumber(item.month),
    checkIns: item.checkInCount,
    successRate: item.completionRate,
  }));

  const best = monthly.reduce<MonthlyPoint | null>(
    (top, item) => (top === null || item.checkIns > top.checkIns ? item : top),
    null,
  );
  const worst = monthly.reduce<MonthlyPoint | null>(
    (low, item) =>
      low === null || item.successRate < low.successRate ? item : low,
    null,
  );

  return {
    totalCheckIns: summary.totalCheckInCount,
    bestStreak: summary.bestStreakEver,
    successRate: summary.averageCompletionRate,
    succeeded: summary.completedDayCount,
    missed: summary.missedDayCount,
    completedChallenges: summary.completedChallengeCount,
    inProgressChallenges: summary.inProgressChallengeCount,
    grass,
    monthly,
    bestMonth: best?.month ?? 0,
    bestMonthCheckIns: best?.checkIns ?? 0,
    worstMonth: worst?.month ?? 0,
    worstMonthRate: worst?.successRate ?? 0,
    categories: toCategoryStats(categoryBreakdown),
  };
}

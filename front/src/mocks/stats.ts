// 목 데이터. 실 API 가 붙으면 이 폴더는 통째로 지운다.
//
// 기능정의서 PRO-05 의 화면 값이다.

import type { CategoryStat, MonthlyPoint } from "./types";

export const STATS_PERIODS = ["ALL", "YEAR", "SIX_MONTHS"] as const;
export type StatsPeriod = (typeof STATS_PERIODS)[number];

export const STATS_PERIOD_LABEL: Record<StatsPeriod, string> = {
  ALL: "전체 기간",
  YEAR: "최근 1년",
  SIX_MONTHS: "최근 6개월",
};

export const SUMMARY = {
  totalCheckIns: 286,
  bestStreak: 27,
  successRate: 82,
  succeeded: 286,
  missed: 64,
  completedChallenges: 12,
  abandonedChallenges: 3,
};

export const MONTHLY: MonthlyPoint[] = [
  { month: 3, checkIns: 31, successRate: 72 },
  { month: 4, checkIns: 36, successRate: 76 },
  { month: 5, checkIns: 42, successRate: 81 },
  { month: 6, checkIns: 48, successRate: 84 },
  { month: 7, checkIns: 53, successRate: 87 },
  { month: 8, checkIns: 56, successRate: 90 },
];

export const MONTHLY_HIGHLIGHT = {
  bestMonth: 8,
  bestMonthCheckIns: 56,
  worstMonth: 3,
  worstMonthMissed: 12,
};

// missedShare 합이 100 이다.
export const CATEGORY_STATS: CategoryStat[] = [
  { category: "EXERCISE", successRate: 92, checkIns: 72, missedShare: 4 },
  { category: "STUDY", successRate: 84, checkIns: 58, missedShare: 7 },
  { category: "READING", successRate: 71, checkIns: 44, missedShare: 11 },
  { category: "HEALTH", successRate: 63, checkIns: 37, missedShare: 18 },
  { category: "ETC", successRate: 58, checkIns: 30, missedShare: 15 },
  { category: "LIFE", successRate: 48, checkIns: 26, missedShare: 21 },
  { category: "JOB", successRate: 39, checkIns: 19, missedShare: 24 },
];

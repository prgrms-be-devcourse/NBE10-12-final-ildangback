/**
 * 화면이 데이터를 얻는 자리. **여기가 교체 지점이다.**
 *
 * 실 API 가 나오면 이 파일 대신 `domains/record/api.ts` 를 만들고 화면의 import
 * 만 바꾸면 된다. 상점이 그렇게 빠져나갔다 — `domains/item/api.ts` 를 보면 된다.
 * 남은 것은 홈과 개인 통계뿐이고, 둘 다 `domain/record` 가 main 에 오면 지운다.
 *
 * 개발 중 상태 확인:
 *   ?mock=loading  계속 로딩
 *   ?mock=error    실패 화면
 *   ?mock=empty    빈 화면
 */
import { buildCheckInDays, daysAgoKey } from "./grass";
import { CATEGORY_STATS, MONTHLY, MONTHLY_HIGHLIGHT, SUMMARY } from "./stats";
import type { HomeData, PersonalStatsData } from "./types";

const LATENCY = 350;

// 4개월.
const HOME_GRASS_WEEKS = 18;
const STATS_GRASS_WEEKS = 52;

function mockMode(): string | null {
  if (typeof window === "undefined") return null;
  return new URLSearchParams(window.location.search).get("mock");
}

/** 실제 왕복처럼 지연을 준다. 즉시 resolve 하면 로딩 화면을 볼 수 없다. */
function respond<T>(value: T): Promise<T> {
  const mode = mockMode();
  if (mode === "loading") return new Promise<T>(() => {});
  if (mode === "error") {
    return new Promise((_, reject) =>
      setTimeout(() => reject(new Error("mock error")), LATENCY),
    );
  }
  return new Promise((resolve) => setTimeout(() => resolve(value), LATENCY));
}

const EMPTY_HOME: HomeData = {
  point: 0,
  streakDays: 0,
  monthlyCheckIns: 0,
  monthlySuccessRate: 0,
  todayChallenges: [],
  grass: buildCheckInDays(HOME_GRASS_WEEKS).map((d) => ({ ...d, count: 0 })),
  recentActivities: [],
};

export function fetchHome(): Promise<HomeData> {
  if (mockMode() === "empty") return respond(EMPTY_HOME);

  return respond({
    point: 1240,
    streakDays: 12,
    monthlyCheckIns: 18,
    monthlySuccessRate: 82,
    todayChallenges: [
      { id: 1, name: "오운완", category: "EXERCISE", done: 1, goal: 3 },
      { id: 2, name: "알고리즘 매일 풀기", category: "DEV", done: 2, goal: 2 },
      { id: 3, name: "하루 30분 독서", category: "READING", done: 0, goal: 1 },
    ],
    grass: buildCheckInDays(HOME_GRASS_WEEKS, 12),
    recentActivities: [
      {
        id: 1,
        prefix: "feat",
        title: "알고리즘 1문제 풀이",
        date: daysAgoKey(0),
        point: 40,
      },
      {
        id: 2,
        prefix: "docs",
        title: "독서 30분 기록",
        date: daysAgoKey(1),
        point: 40,
      },
      {
        id: 3,
        prefix: "workout",
        title: "오운완 인증",
        date: daysAgoKey(2),
        point: 40,
      },
    ],
  } satisfies HomeData);
}

const EMPTY_STATS: PersonalStatsData = {
  totalCheckIns: 0,
  bestStreak: 0,
  successRate: 0,
  succeeded: 0,
  missed: 0,
  completedChallenges: 0,
  abandonedChallenges: 0,
  grass: buildCheckInDays(STATS_GRASS_WEEKS).map((d) => ({ ...d, count: 0 })),
  monthly: [],
  bestMonth: 0,
  bestMonthCheckIns: 0,
  worstMonth: 0,
  worstMonthMissed: 0,
  categories: [],
};

/**
 * 기간 선택이 바뀌면 다시 부른다. 목은 기간을 무시하지만 호출은 실제로 다시 나가서,
 * 실 API 로 바꿔도 화면 동작이 그대로다.
 */
export function fetchPersonalStats(period: string): Promise<PersonalStatsData> {
  void period;
  if (mockMode() === "empty") return respond(EMPTY_STATS);

  return respond({
    ...SUMMARY,
    grass: buildCheckInDays(STATS_GRASS_WEEKS, 12),
    monthly: MONTHLY,
    ...MONTHLY_HIGHLIGHT,
    categories: CATEGORY_STATS,
  } satisfies PersonalStatsData);
}

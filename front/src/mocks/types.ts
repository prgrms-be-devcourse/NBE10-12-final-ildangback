/**
 * 화면을 그리는 데 프론트가 필요한 필드 목록이다.
 *
 * **이건 API 명세가 아니다.** `domain/record` 는 아직 빈 폴더라 응답 모양이
 * 정해진 게 없다. 그래서 "API 가 이렇다" 가 아니라 "프론트는 이게 필요하다" 를
 * 적어 둔 것이다.
 *
 * 담당자와 맞춘 뒤에 `shared/api/types.ts` 로 옮긴다.
 * record(홈 지표 · 잔디 · 통계) 한철완.
 * 상점과 캐릭터는 붙였다 — `domains/item/lib/shop.ts` 로 옮겨 갔다.
 *
 * 홈은 주인이 애매하다 — 지표는 record, 오늘의 챌린지는 challenge,
 * 포인트는 point 라서 어느 도메인이 모아 줄지 팀에서 정해야 한다.
 */
import type { Category } from "./categories";

/** 잔디 한 칸. 서버는 날짜와 횟수만 주면 된다. 오늘/미래 판정은 프론트가 한다. */
export interface CheckInDay {
  /** YYYY-MM-DD. 타임존이 없는 값이라 문자열 그대로 다룬다. */
  date: string;
  count: number;
}

export interface TodayChallenge {
  id: number;
  name: string;
  category: Category;
  /** 오늘 한 인증 횟수 */
  done: number;
  /** 오늘 채워야 하는 횟수 */
  goal: number;
}

export interface RecentActivity {
  id: number;
  /** 활동 종류. 화면에는 `feat:` 처럼 앞에 붙는다. */
  prefix: string;
  title: string;
  /** YYYY-MM-DD */
  date: string;
  /** 이 활동으로 받은 포인트. 없으면 0. */
  point: number;
}

export interface HomeData {
  point: number;
  streakDays: number;
  monthlyCheckIns: number;
  monthlySuccessRate: number;
  todayChallenges: TodayChallenge[];
  grass: CheckInDay[];
  recentActivities: RecentActivity[];
}

export interface MonthlyPoint {
  month: number;
  checkIns: number;
  successRate: number;
}

export interface CategoryStat {
  category: Category;
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
  abandonedChallenges: number;
  grass: CheckInDay[];
  monthly: MonthlyPoint[];
  bestMonth: number;
  bestMonthCheckIns: number;
  worstMonth: number;
  worstMonthMissed: number;
  categories: CategoryStat[];
}

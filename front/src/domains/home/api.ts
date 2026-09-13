import { apiFetch } from "../../shared/api/client";
import type {
  ActivityResponse,
  GrassResponse,
  HomeResponse,
  SliceResponse,
} from "../../shared/api/types";
import type { GrassDay } from "../../shared/ui/ContributionGrid";

export function getHome(): Promise<HomeResponse> {
  return apiFetch("/api/home");
}

/** 최근 활동. 서버가 3건으로 잘라서 준다. */
export function getActivities(): Promise<SliceResponse<ActivityResponse>> {
  return apiFetch("/api/users/me/activities");
}

/**
 * 잔디. from 부터 to 까지 인증이 없는 날도 0 으로 채워서 한 번에 온다.
 * 366일을 넘기면 서버가 INVALID_INPUT_VALUE 로 거부한다.
 */
export function getGrass(
  from: string,
  to: string,
): Promise<SliceResponse<GrassResponse>> {
  return apiFetch(`/api/users/me/grass?from=${from}&to=${to}`);
}

function dateKey(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * 잔디 격자가 요구하는 모양으로 받아 온다 - 일요일에 시작해 7의 배수로 채운 배열.
 * 오늘이 든 주의 토요일에서 끝나는 weeks 주치다.
 */
export async function getGrassWeeks(weeks: number): Promise<GrassDay[]> {
  const to = new Date();
  to.setHours(0, 0, 0, 0);
  to.setDate(to.getDate() + (6 - to.getDay()));

  const from = new Date(to);
  from.setDate(from.getDate() - (weeks * 7 - 1));

  const { content } = await getGrass(dateKey(from), dateKey(to));
  return content.map((cell) => ({ date: cell.date, count: cell.checkInCount }));
}

export interface HomeData {
  home: HomeResponse;
  grass: GrassDay[];
  activities: ActivityResponse[];
}

/** 홈 화면 한 벌. 셋을 한꺼번에 부른다. */
export async function fetchHome(weeks: number): Promise<HomeData> {
  const [home, grass, activities] = await Promise.all([
    getHome(),
    getGrassWeeks(weeks),
    getActivities(),
  ]);

  return { home, grass, activities: activities.content };
}

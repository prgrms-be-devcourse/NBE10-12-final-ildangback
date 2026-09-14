/**
 * 월 필터 유틸. 값은 "yyyy-MM" 문자열. 갤러리 탭 · 프로필 모아보기 · 일일 로그가 공유한다.
 * Date 로 파싱하지만 "1일 정오" 기준이라 타임존 경계에서 월이 밀리지 않는다.
 */

export function currentMonth(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

export function shiftMonth(month: string, delta: number): string {
  const [y, m] = month.split("-").map(Number);
  const d = new Date(y, m - 1 + delta, 12);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/** "2026-08" → "2026년 8월" */
export function monthLabel(month: string): string {
  const [y, m] = month.split("-");
  return `${y}년 ${Number(m)}월`;
}

/** 최근 `count` 개월을 최신순으로. 월 드롭다운 옵션용. */
export function recentMonths(count = 12): string[] {
  const now = currentMonth();
  return Array.from({ length: count }, (_, i) => shiftMonth(now, -i));
}

/** "yyyy-MM-dd" → "yyyy-MM". 챌린지 시작·종료일을 월 네비 범위로 좁힐 때 쓴다. */
export function monthOf(date: string): string {
  return date.slice(0, 7);
}

/**
 * 월 네비 범위를 좁히는 데 쓰는 시작·종료일 쌍. 갤러리 탭 · 일일 로그가 챌린지로부터
 * 이 하나로 받아, startDate/endDate 를 따로따로 꿰어 나르지 않는다.
 */
export interface DatePeriod {
  startDate?: string;
  endDate?: string;
}

/** `period` 로부터 MonthNav 의 minMonth/maxMonth 를 한 번에 계산한다. */
export function monthRangeOf(period?: DatePeriod): {
  minMonth?: string;
  maxMonth?: string;
} {
  return {
    minMonth: period?.startDate ? monthOf(period.startDate) : undefined,
    maxMonth: period?.endDate ? monthOf(period.endDate) : undefined,
  };
}

/** `month` 가 [minMonth, maxMonth] 밖이면 범위 안으로 당겨온다. 월 네비 초기값 clamp 용. */
export function clampToRange(
  month: string,
  minMonth?: string,
  maxMonth?: string,
): string {
  if (minMonth != null && month < minMonth) return minMonth;
  if (maxMonth != null && month > maxMonth) return maxMonth;
  return month;
}

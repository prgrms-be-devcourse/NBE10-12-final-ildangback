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

/**
 * 서버의 createdAt 은 LocalDateTime 이라 타임존 오프셋이 없다.
 * new Date() 로 파싱하면 브라우저 로컬 시각으로 해석해서 KST 밖에서 날짜가 밀린다.
 * 날짜만 보여주면 되므로 문자열 앞 10자를 그대로 쓴다 — 파싱 자체를 하지 않는다.
 */
export function formatDate(isoLocalDateTime: string): string {
  const [year, month, day] = isoLocalDateTime.slice(0, 10).split("-");
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}

/** 연도 없는 날짜. 포인트 내역처럼 최근 기록을 날짜별로 묶는 헤더에 쓴다. */
export function formatMonthDay(isoLocalDateTime: string): string {
  const [, month, day] = isoLocalDateTime.slice(0, 10).split("-");
  return `${Number(month)}월 ${Number(day)}일`;
}

/** "09:12" 형태의 시:분만. */
export function formatTime(isoLocalDateTime: string): string {
  return isoLocalDateTime.slice(11, 16);
}

/** "2026.08.25 09:12" 형태. 포인트 상세처럼 처리 일시를 정확히 보여줄 때 쓴다. */
export function formatDateTimeDot(isoLocalDateTime: string): string {
  const datePart = isoLocalDateTime.slice(0, 10).replaceAll("-", ".");
  return `${datePart} ${formatTime(isoLocalDateTime)}`;
}

/** 날짜만 비교할 때 쓰는 키(YYYY-MM-DD). 목록을 날짜별로 묶을 때 그룹 키로 쓴다. */
export function dateKey(isoLocalDateTime: string): string {
  return isoLocalDateTime.slice(0, 10);
}

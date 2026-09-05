/**
 * 서버의 createdAt 은 LocalDateTime 이라 타임존 오프셋이 없다.
 * new Date() 로 파싱하면 브라우저 로컬 시각으로 해석해서 KST 밖에서 날짜가 밀린다.
 * 날짜만 보여주면 되므로 문자열 앞 10자를 그대로 쓴다 — 파싱 자체를 하지 않는다.
 */
export function formatDate(isoLocalDateTime: string): string {
  const [year, month, day] = isoLocalDateTime.slice(0, 10).split("-");
  return `${year}년 ${Number(month)}월 ${Number(day)}일`;
}

/** "2026-08-25T..." → "8월 25일". 날짜 그룹 헤더용. 파싱 안 함(위 참고). */
export function formatMonthDay(isoLocalDate: string): string {
  const [, month, day] = isoLocalDate.slice(0, 10).split("-");
  return `${Number(month)}월 ${Number(day)}일`;
}

/** "2026-09-02T14:32:54" → "9월 2일 14:32". 라이트박스 시간 표시용. */
export function formatDateTimeMinute(isoLocalDateTime: string): string {
  const [, month, day] = isoLocalDateTime.slice(0, 10).split("-");
  const time = isoLocalDateTime.slice(11, 16);
  return `${Number(month)}월 ${Number(day)}일 ${time}`;
}

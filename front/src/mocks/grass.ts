// 목 데이터. 실 API 가 붙으면 이 폴더는 통째로 지운다.

import type { CheckInDay } from "./types";

/** 날짜 문자열을 섞어 만든 0~99. 새로고침해도 같은 그림이 나온다. */
function noise(date: string): number {
  let hash = 0;
  for (let i = 0; i < date.length; i += 1) {
    hash = (hash * 31 + date.charCodeAt(i)) >>> 0;
  }
  return hash % 100;
}

// 낮을수록 활동이 많다.
// 성공률 82% 와 연속 12일을 말하는 화면이라 빈칸은 20% 안팎이어야 앞뒤가 맞는다.
const THRESHOLDS = [22, 45, 68, 87];

function toCount(bucket: number): number {
  const clamped = Math.max(0, Math.min(99, bucket));
  let count = 0;
  for (const t of THRESHOLDS) {
    if (clamped < t) return count;
    count += 1;
  }
  return count;
}

function toKey(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/**
 * 오늘이 든 주의 토요일에서 끝나는 weeks 주치. 일요일 시작으로 정렬돼 있다.
 *
 * streakDays 를 받는 이유는 화면이 "연속 인증 12일" 이라고 말하는데 잔디 최근
 * 12칸이 비어 있으면 거짓말이 되기 때문이다. 응답 하나가 앞뒤 맞아야 한다.
 */
export function buildCheckInDays(weeks: number, streakDays = 0): CheckInDay[] {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const todayKey = toKey(today);

  const cursor = new Date(today);
  cursor.setDate(cursor.getDate() + (6 - cursor.getDay()) - (weeks * 7 - 1));

  const days: CheckInDay[] = [];
  for (let i = 0; i < weeks * 7; i += 1) {
    const date = toKey(cursor);
    const weekday = cursor.getDay();
    const daysAgo = Math.round((today.getTime() - cursor.getTime()) / 86400000);

    // 주말은 덜 하고, 최근일수록 조금 더 촘촘하다.
    let bucket = noise(date);
    if (weekday === 0 || weekday === 6) bucket += 18;
    bucket -= Math.round(Math.max(0, 1 - daysAgo / (weeks * 7)) * 14);

    const inStreak = daysAgo >= 0 && daysAgo < streakDays;
    const count =
      date > todayKey ? 0 : Math.max(inStreak ? 1 : 0, toCount(bucket));

    days.push({ date, count });
    cursor.setDate(cursor.getDate() + 1);
  }
  return days;
}

/** 오늘에서 며칠 전 날짜. 최근 활동처럼 상대 날짜가 필요한 목에 쓴다. */
export function daysAgoKey(daysAgo: number): string {
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  return toKey(date);
}

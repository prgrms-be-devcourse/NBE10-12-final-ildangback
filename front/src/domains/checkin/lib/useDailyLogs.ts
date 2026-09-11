import { getDailyLogs } from "../api";
import type { DailyLog, DailyLogPageMeta } from "../types";
import { useCursorPage } from "./useCursorPage";

/** 일일 로그 탭 커서 무한스크롤. */
export function useDailyLogs(challengeId: number, month: string) {
  return useCursorPage<DailyLog, DailyLogPageMeta>(
    (cursor) => getDailyLogs(challengeId, { month, cursor }),
    [challengeId, month],
  );
}

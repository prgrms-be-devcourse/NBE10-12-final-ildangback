import { getChallengeGallery } from "../api";
import type { CheckIn } from "../types";
import { useCursorPage } from "./useCursorPage";

interface Filters {
  /** yyyy-MM */
  month: string;
  /** null = 전체 참여자 */
  userId: number | null;
}

/** 챌린지 갤러리 탭 커서 무한스크롤. */
export function useCheckInGallery(challengeId: number, filters: Filters) {
  const { month, userId } = filters;
  return useCursorPage<CheckIn>(
    (cursor) =>
      getChallengeGallery(challengeId, {
        month,
        userId: userId ?? undefined,
        cursor,
      }),
    [challengeId, month, userId],
  );
}

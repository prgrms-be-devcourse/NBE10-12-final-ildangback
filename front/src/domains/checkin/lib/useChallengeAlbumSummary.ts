import { getChallengeAlbumSummary } from "../api";
import type { ChallengeAlbumSummary } from "../types";
import { useFetchOnce } from "./useFetchOnce";

/** 그룹 앨범 헤더용 챌린지 요약. 로딩 중이거나 실패하면 null. */
export function useChallengeAlbumSummary(
  challengeId: number,
): ChallengeAlbumSummary | null {
  return useFetchOnce<ChallengeAlbumSummary | null>(
    () =>
      Number.isNaN(challengeId)
        ? Promise.resolve(null)
        : getChallengeAlbumSummary(challengeId),
    [challengeId],
    null,
  );
}

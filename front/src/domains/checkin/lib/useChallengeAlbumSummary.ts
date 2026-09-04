import { useEffect, useState } from "react";
import { getChallengeAlbumSummary } from "../api";
import type { ChallengeAlbumSummary } from "../types";

/** 그룹 앨범 헤더용 챌린지 요약. 로딩 중이면 null. */
export function useChallengeAlbumSummary(challengeId: number) {
  const [summary, setSummary] = useState<ChallengeAlbumSummary | null>(null);

  useEffect(() => {
    if (Number.isNaN(challengeId)) return;
    let cancelled = false;
    getChallengeAlbumSummary(challengeId)
      .then((next) => {
        if (!cancelled) setSummary(next);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  return summary;
}

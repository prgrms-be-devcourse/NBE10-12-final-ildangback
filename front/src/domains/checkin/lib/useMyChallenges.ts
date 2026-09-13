import { getMyChallenges } from "../api";
import type { MyChallengeSummary } from "../types";
import { useFetchOnce } from "./useFetchOnce";

const NONE: MyChallengeSummary[] = [];

/**
 * 내가 참여한(했던) 챌린지 목록. "전체 인증" 화면 챌린지 드롭다운용.
 * 실패해도 화면을 막지 않는다 — 드롭다운이 "전체 챌린지" 하나로 줄어든다.
 */
export function useMyChallenges(): MyChallengeSummary[] {
  return useFetchOnce(getMyChallenges, [], NONE);
}

import { useEffect, useState } from "react";
import { getChallengeMembers } from "../api";
import type { ChallengeMember } from "../types";

/**
 * 챌린지 참여자 목록. 갤러리 참여자 필터 칩에 쓴다.
 *
 * `GET /challenges/{id}/members` 는 feat/6-group-challenge(#31)에 있다.
 * #31 머지 전까지만 dev 스텁 응답. 실패해도 화면을 막지 않는다 — 칩이 "전체"
 * 하나로 줄어든다.
 */
export function useChallengeMembers(challengeId: number) {
  const [members, setMembers] = useState<ChallengeMember[]>([]);

  useEffect(() => {
    if (Number.isNaN(challengeId)) return;
    let cancelled = false;
    getChallengeMembers(challengeId)
      .then((next) => {
        if (!cancelled) setMembers(next);
      })
      .catch(() => {
        if (!cancelled) setMembers([]);
      });
    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  return members;
}

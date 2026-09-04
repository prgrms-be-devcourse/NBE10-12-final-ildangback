import { getMyCheckIns } from "../api";
import type { CheckInType, MyCheckIn, MyCheckInPageMeta } from "../types";
import { useCursorPage } from "./useCursorPage";

interface Filters {
  /** yyyy-MM */
  month: string;
  /** 지정 시 그룹 앨범(해당 챌린지만). 생략 시 전체 인증. */
  challengeId?: number;
  checkInType?: CheckInType;
}

/** 프로필 전체 인증 / 그룹 앨범 커서 무한스크롤. */
export function useMyCheckIns(filters: Filters) {
  const { month, challengeId, checkInType } = filters;
  return useCursorPage<MyCheckIn, MyCheckInPageMeta>(
    (cursor) => getMyCheckIns({ month, challengeId, checkInType, cursor }),
    [month, challengeId, checkInType],
  );
}

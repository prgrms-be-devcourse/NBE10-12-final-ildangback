import {
  CheckSquareIcon,
  GitMergeIcon,
  ImageIcon,
  type Icon,
} from "@phosphor-icons/react";
import type { GroupPointReason } from "../../../shared/api/types";

/** 목록 항목 제목. 백엔드 GroupPointReason 과 1:1 대응한다. */
export const GROUP_REASON_TITLE: Record<GroupPointReason, string> = {
  DAILY_ALL_COMPLETE: "전원 인증 완료 보너스",
  MERGE_BONUS: "머지 완주 보너스",
  BACKGROUND_PURCHASE: "배경 구매",
};

/** 상세 화면 "내역 유형" 필드용 짧은 표현. */
export const GROUP_REASON_TYPE_LABEL: Record<GroupPointReason, string> = {
  DAILY_ALL_COMPLETE: "전원 인증 보너스",
  MERGE_BONUS: "머지 보너스",
  BACKGROUND_PURCHASE: "배경 구매",
};

const GROUP_REASON_VERB: Record<GroupPointReason, string> = {
  DAILY_ALL_COMPLETE: "멤버 전원 인증으로 적립했어요",
  MERGE_BONUS: "완주로 적립했어요",
  BACKGROUND_PURCHASE: "구매로 사용했어요",
};

export function groupReasonSentence(
  reason: GroupPointReason,
  sourceName: string,
): string {
  return `${sourceName} ${GROUP_REASON_VERB[reason]}`;
}

export const GROUP_REASON_ICON: Record<GroupPointReason, Icon> = {
  DAILY_ALL_COMPLETE: CheckSquareIcon,
  MERGE_BONUS: GitMergeIcon,
  BACKGROUND_PURCHASE: ImageIcon,
};

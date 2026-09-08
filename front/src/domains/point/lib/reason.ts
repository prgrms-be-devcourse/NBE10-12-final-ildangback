import {
  CheckSquareIcon,
  GitMergeIcon,
  MinusCircleIcon,
  ShoppingBagIcon,
  type Icon,
} from "@phosphor-icons/react";
import type { UserPointReason } from "../../../shared/api/types";

/** 목록 항목 제목. 백엔드 UserPointReason 과 1:1 대응한다. */
export const REASON_TITLE: Record<UserPointReason, string> = {
  CHECK_IN: "챌린지 인증 완료",
  CHALLENGE_BONUS: "챌린지 완주 보너스",
  MONTHLY_MERGE_BONUS: "월간 머지 완주 보너스",
  ITEM_PURCHASE: "아이템 구매",
  WITHDRAWAL_PENALTY: "챌린지 중도 탈퇴 차감",
};

/** 상세 화면 "내역 유형" 필드용 짧은 표현. */
export const REASON_TYPE_LABEL: Record<UserPointReason, string> = {
  CHECK_IN: "인증 적립",
  CHALLENGE_BONUS: "완주 보너스",
  MONTHLY_MERGE_BONUS: "머지 보너스",
  ITEM_PURCHASE: "아이템 구매",
  WITHDRAWAL_PENALTY: "탈퇴 차감",
};

const REASON_VERB: Record<UserPointReason, string> = {
  CHECK_IN: "인증으로 적립했어요",
  CHALLENGE_BONUS: "완주로 적립했어요",
  MONTHLY_MERGE_BONUS: "완주로 적립했어요",
  ITEM_PURCHASE: "구매로 사용했어요",
  WITHDRAWAL_PENALTY: "중도 탈퇴로 차감됐어요",
};

/** 상세 화면 히어로 문구. "오운완 인증으로 적립했어요" 처럼 sourceName 과 이어붙인다. */
export function reasonSentence(
  reason: UserPointReason,
  sourceName: string,
): string {
  return `${sourceName} ${REASON_VERB[reason]}`;
}

export const REASON_ICON: Record<UserPointReason, Icon> = {
  CHECK_IN: CheckSquareIcon,
  CHALLENGE_BONUS: GitMergeIcon,
  MONTHLY_MERGE_BONUS: GitMergeIcon,
  ITEM_PURCHASE: ShoppingBagIcon,
  WITHDRAWAL_PENALTY: MinusCircleIcon,
};

/** 목록 아이콘 배지 색. 적립이면 보라, 차감이면 회색. */
export function reasonIconTone(amount: number): { bg: string; fg: string } {
  return amount > 0
    ? { bg: "bg-purple-100", fg: "text-purple-600" }
    : { bg: "bg-gray-200", fg: "text-gray-700" };
}

export const PERIOD_LABEL: Record<
  "THIS_MONTH" | "LAST_MONTH" | "ALL" | "CUSTOM",
  string
> = {
  THIS_MONTH: "이번 달",
  LAST_MONTH: "지난 달",
  ALL: "전체 기간",
  CUSTOM: "직접설정",
};

export const CHANGE_TYPE_LABEL: Record<"EARN" | "DEDUCT" | "ALL", string> = {
  ALL: "전체 내역",
  EARN: "적립만",
  DEDUCT: "사용만",
};

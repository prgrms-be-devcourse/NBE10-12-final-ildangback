import { CheckIcon } from "@phosphor-icons/react";
import { Button } from "../../../shared/ui/Button";
import type { CheckInResultResponse } from "../types";

interface Props {
  result: CheckInResultResponse;
  onGoToGroup: () => void;
}

/**
 * 와이어프레임 4번 — 인증 완료.
 *
 * 그룹 포인트 행은 넣지 않는다: 그룹 포인트는 체크인 시점이 아니라 daily-log 생성 시점에
 * 지급되고 이 API 응답에 없다(CONTEXT.md / checkin-api-spec.yml).
 */
export function CheckInDone({ result, onGoToGroup }: Props) {
  const {
    currentCount,
    targetCount,
    dailyCompleted,
    earnedUserPoints,
    currentStreak,
    groupCompletedCount,
    groupTotalCount,
  } = result;
  const remaining = Math.max(0, targetCount - currentCount);

  return (
    <div className="flex flex-1 flex-col items-center px-6 pt-6 pb-10">
      <span className="flex size-16 items-center justify-center rounded-2xl bg-purple-500 text-white">
        <CheckIcon size={32} weight="bold" />
      </span>

      <p className="mt-8 rounded-full bg-purple-50 px-4 py-1.5 text-[14px] font-semibold text-purple-600">
        🔥 {currentStreak}일 연속 달성 중
      </p>

      {dailyCompleted ? (
        <section className="mt-8 w-full rounded-2xl border border-purple-100 px-5 py-4">
          <h2 className="text-[15px] font-bold text-gray-900">
            오늘 받은 포인트
          </h2>
          <div className="mt-3 flex items-center justify-between text-[14px]">
            <span className="text-gray-500">개인 포인트</span>
            <span className="font-bold text-purple-600">
              +{earnedUserPoints}P
            </span>
          </div>
        </section>
      ) : (
        <p className="mt-8 w-full rounded-2xl border border-purple-100 px-5 py-4 text-center text-[14px] text-gray-600">
          오늘 {currentCount}/{targetCount}회 — {remaining}회 더 인증하면
          포인트를 받아요
        </p>
      )}

      <section className="mt-4 w-full rounded-2xl border border-purple-100 px-5 py-4 text-[14px]">
        <div className="flex items-center justify-between">
          <span className="text-gray-500">오늘 인증</span>
          <span className="font-semibold text-purple-600">
            {currentCount} / {targetCount}회
          </span>
        </div>
        <div className="mt-3 flex items-center justify-between">
          <span className="text-gray-500">그룹 완료 현황</span>
          <span className="font-semibold text-purple-600">
            {groupCompletedCount} / {groupTotalCount}명
          </span>
        </div>
      </section>

      <Button className="mt-8" onClick={onGoToGroup}>
        그룹 현황으로 이동
      </Button>
    </div>
  );
}

import { Fragment } from "react";
import { MergeStatusCircle, type MergeStatus } from "./MergeStatusCircle";
import { computeMergeWindow } from "../lib/mergeWindow";

interface MergeCircleHeaderProps {
  totalCount: number;
  completedCount: number;
  hasCurrentCycle: boolean;
}

const WINDOW_SIZE = 5;

// "월간 머지 목록" 화면 상단의 가로 원형 스테퍼. 완료는 실선으로, 나머지는
// 옅은 선으로 이어진다. 항상 5칸을 양 끝까지 꽉 채워서 보여준다 - 5개가 안 되면
// 남는 칸은 빈 자리(placeholder)로 채워서 챌린지마다 폭이 달라지지 않게 한다.
export function MergeCircleHeader({
  totalCount,
  completedCount,
  hasCurrentCycle,
}: MergeCircleHeaderProps) {
  const { indices } = computeMergeWindow(
    totalCount,
    completedCount,
    hasCurrentCycle,
    WINDOW_SIZE,
  );
  const placeholderCount = WINDOW_SIZE - indices.length;

  const statuses: MergeStatus[] = [
    ...indices.map((index): MergeStatus => {
      if (index < completedCount) return "completed";
      if (index === completedCount && hasCurrentCycle) return "current";
      return "upcoming";
    }),
    ...Array.from({ length: placeholderCount }, (): MergeStatus => "upcoming"),
  ];

  return (
    <div className="flex w-full items-center">
      {statuses.map((status, i) => (
        <Fragment key={i}>
          {i > 0 && (
            <div
              className={`h-px flex-1 ${
                status === "completed" || status === "current"
                  ? "bg-[#8453C8]"
                  : "bg-[#E4E1EA]"
              }`}
              aria-hidden
            />
          )}
          <MergeStatusCircle status={status} size={28} />
        </Fragment>
      ))}
    </div>
  );
}

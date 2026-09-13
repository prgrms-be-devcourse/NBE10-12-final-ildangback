import { CheckIcon, CircleNotchIcon } from "@phosphor-icons/react";
import { computeMergeWindow } from "../lib/mergeWindow";

interface MergeProgressDotsProps {
  totalCount: number;
  completedCount: number;
  /** 진행 중인 회차가 있으면(=아직 안 끝났으면) true. */
  hasCurrentCycle: boolean;
}

const WINDOW_SIZE = 5;

// 완료(보라 채움+체크) / 진행중(흰 배경+보라 테두리+천천히 도는 로딩 아이콘) /
// 예정(회색 배경+연한 테두리) 3가지 상태를 32x32 원형으로 표시한다. 최대 5개만
// 보여준다. 점선은 도트가 2개 이상일 때만(사이를 이어줄 게 있을 때만) 그린다.
export function MergeProgressDots({
  totalCount,
  completedCount,
  hasCurrentCycle,
}: MergeProgressDotsProps) {
  const { indices } = computeMergeWindow(
    totalCount,
    completedCount,
    hasCurrentCycle,
    WINDOW_SIZE,
  );

  return (
    <div className="relative">
      {indices.length > 1 && (
        <div
          className="absolute top-1/2 right-4 left-4 -translate-y-1/2 border-t border-dashed border-[#D2D2D2]"
          aria-hidden
        />
      )}
      <div className="relative flex justify-between">
        {indices.map((index) => {
          const isCompleted = index < completedCount;
          const isCurrent =
            !isCompleted && index === completedCount && hasCurrentCycle;

          return (
            <div
              key={index}
              className={`flex h-8 w-8 items-center justify-center rounded-full ${
                isCompleted
                  ? "bg-[#8551C9]"
                  : isCurrent
                    ? "border border-[#8453C8] bg-white"
                    : "border border-[#DEDDDF] bg-[#EEEEEE]"
              }`}
            >
              {isCompleted && (
                <CheckIcon size={18} weight="bold" className="text-white" />
              )}
              {isCurrent && (
                <CircleNotchIcon
                  size={16}
                  weight="bold"
                  className="animate-[spin_2.5s_linear_infinite] text-[#8453C8]"
                />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

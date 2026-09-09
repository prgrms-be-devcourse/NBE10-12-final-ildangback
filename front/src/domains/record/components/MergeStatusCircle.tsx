import { CheckIcon } from "@phosphor-icons/react";

export type MergeStatus = "completed" | "current" | "upcoming";

// 원형 상태 마커. 완료(보라 채움+체크) / 진행중(흰 배경+보라 링+가운데 점) /
// 예정(연한 회색) 3가지. 머지 목록 왼쪽 세로 타임라인에서 쓴다.
export function MergeStatusCircle({
  status,
  size = 32,
}: {
  status: MergeStatus;
  size?: number;
}) {
  if (status === "completed") {
    return (
      <div
        style={{ width: size, height: size }}
        className="flex shrink-0 items-center justify-center rounded-full bg-[#8551C9]"
      >
        <CheckIcon size={size * 0.55} weight="bold" className="text-white" />
      </div>
    );
  }

  if (status === "current") {
    return (
      <div
        style={{ width: size, height: size }}
        className="flex shrink-0 items-center justify-center rounded-full border-2 border-[#8453C8] bg-white"
      >
        <span
          className="rounded-full bg-[#8453C8]"
          style={{ width: size * 0.45, height: size * 0.45 }}
        />
      </div>
    );
  }

  return (
    <div
      style={{ width: size, height: size }}
      className="shrink-0 rounded-full border border-[#DEDDDF] bg-[#EEEEEE]"
    />
  );
}

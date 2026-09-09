/** MergeProgressDots가 쓰는 슬라이딩 윈도우 계산. 전체 회차 수가 windowSize를
 * 넘으면(예: 1년 챌린지 12회차) 진행 중인 회차가 창 안에 들어오도록 앞쪽(이미
 * 지나간) 회차부터 밀어낸다. */
export interface MergeWindowResult {
  /** 창에 표시할 회차 index(0-based) 목록. */
  indices: number[];
}

export function computeMergeWindow(
  totalCount: number,
  completedCount: number,
  hasCurrentCycle: boolean,
  windowSize: number,
): MergeWindowResult {
  // anchor: 창 중심에 두고 싶은 회차. 진행 중인 회차가 있으면 그 회차,
  // 없으면(전부 완료) 마지막 완료 회차.
  const anchor = hasCurrentCycle
    ? completedCount
    : Math.max(completedCount - 1, 0);
  // anchor를 창의 정중앙(windowSize개 중 앞쪽 절반만큼 당긴 위치)에 두되,
  // 전체 범위([0, totalCount - windowSize])를 벗어나지 않게 clamp한다.
  const halfWindow = Math.floor(windowSize / 2);
  const start =
    totalCount <= windowSize
      ? 0
      : Math.min(Math.max(anchor - halfWindow, 0), totalCount - windowSize);
  const end = Math.min(start + windowSize, totalCount);
  const indices = Array.from({ length: end - start }, (_, i) => start + i);

  return { indices };
}

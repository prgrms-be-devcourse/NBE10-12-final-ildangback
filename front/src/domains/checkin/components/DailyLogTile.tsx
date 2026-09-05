import { CheckIcon, PlayCircleIcon } from "@phosphor-icons/react";
import type { DailyLog } from "../types";

interface Props {
  log: DailyLog;
  /** videoUrl 이 있을 때만 호출된다(풀스크린 재생). */
  onPlay: () => void;
}

/**
 * 하루치 일일 로그 타일.
 *
 * 실제로는 서버가 참여자 인증을 참여 인원수(`totalCount`)에 맞춰 타일로 합친 영상
 * 1개다 — 프론트는 `<video>` 하나만 그린다. `videoUrl` 이 오기 전(영상 생성 대기)에는
 * 같은 타일 배치로 placeholder 를 그려 전환이 튀지 않게 한다: `completedCount` 칸은
 * 체크 표시, 나머지는 검정. `DailyLog` 응답에 멤버별 실제 사진은 없으므로(합성
 * 대상은 영상 하나) 진짜 사진 대신 "완료" 표시만 보여준다.
 *
 * 타일 배치(참여 인원 → 그리드): 서버 ffmpeg 도 같은 규칙을 봐야 한다.
 * (front/docs/checkin-gallery-backend-asks.md #5)
 *
 * `completedCount === 0`(인증 대상일인데 아무도 안 함)이면 합성할 소스 자체가
 * 없어 영상이 영영 안 생긴다 — 타일/영상 자리 대신 문구 한 줄만 보여준다.
 */
export function DailyLogTile({ log, onPlay }: Props) {
  if (log.completedCount === 0) {
    return (
      <div className="flex w-full items-center justify-center rounded-lg bg-gray-50 py-8">
        <p className="text-[13px] text-gray-400">
          이날은 아무도 인증하지 않았어요
        </p>
      </div>
    );
  }

  const grid = tileGrid(log.totalCount);

  if (log.videoUrl) {
    return (
      <button
        type="button"
        onClick={onPlay}
        className={`relative block w-full overflow-hidden rounded-lg bg-black ${grid.aspect}`}
      >
        <video
          src={log.videoUrl}
          muted
          loop
          playsInline
          autoPlay
          className="size-full object-cover"
        />
        <PlayCircleIcon
          size={44}
          weight="fill"
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 text-white/80"
        />
      </button>
    );
  }

  return (
    <div
      className={`grid w-full overflow-hidden rounded-lg ${grid.aspect} ${COLS_CLASS[grid.cols]}`}
    >
      {Array.from({ length: grid.slots }, (_, i) =>
        i < log.completedCount ? (
          <div
            key={i}
            className="flex size-full items-center justify-center bg-purple-100"
          >
            <CheckIcon size={20} weight="bold" className="text-purple-400" />
          </div>
        ) : (
          <div key={i} className="size-full bg-black" />
        ),
      )}
    </div>
  );
}

const COLS_CLASS: Record<number, string> = {
  1: "grid-cols-1",
  2: "grid-cols-2",
  3: "grid-cols-3",
};

/**
 * 참여 인원 → 타일 그리드. 각 칸은 정사각(사진 비율 유지, object-cover 로 크롭),
 * 컨테이너 비율만 인원수에 따라 달라진다.
 *   1 → 1칸 정사각 · 2·3 → 가로 한 줄 · 4 → 2×2 · 5·6 → 3×2 (5는 마지막 칸 검정)
 */
function tileGrid(totalCount: number): {
  cols: number;
  slots: number;
  aspect: string;
} {
  const n = Math.min(Math.max(totalCount || 1, 1), 6);
  if (n === 1) return { cols: 1, slots: 1, aspect: "aspect-square" };
  if (n === 2) return { cols: 2, slots: 2, aspect: "aspect-[2/1]" };
  if (n === 3) return { cols: 3, slots: 3, aspect: "aspect-[3/1]" };
  if (n === 4) return { cols: 2, slots: 4, aspect: "aspect-square" };
  return { cols: 3, slots: 6, aspect: "aspect-[3/2]" };
}

import { CheckIcon, PlayCircleIcon } from "@phosphor-icons/react";
import { useEffect, useRef, useState, type Ref } from "react";
import { useAuthedImage } from "../../../shared/lib/useAuthedImage";
import { useAutoPlayInView } from "../lib/useAutoPlayInView";
import type { DailyLog } from "../types";

interface Props {
  log: DailyLog;
  /** 영상이 실제로 받아진 뒤에만 호출된다. 인자는 재생에 쓸 objectURL. */
  onPlay: (src: string) => void;
}

/**
 * 하루치 일일 로그 타일.
 *
 * 서버가 그 날 인증 사진들을 참여 인원수에 맞춘 그리드로 합쳐 만든 영상 1개다 —
 * 프론트는 `<video>` 하나만 그린다. `videoUrl` 이 아직 없으면(생성 대기) 같은 타일
 * 배치로 placeholder 를 그려 전환이 튀지 않게 한다: `completedCount` 칸은 체크 표시,
 * 나머지는 검정. `DailyLog` 응답에 멤버별 사진은 없으므로(합성 대상은 영상 하나)
 * 진짜 사진 대신 "완료" 표시만 보여준다.
 *
 * 타일 배치(참여 인원 -> 그리드)는 서버 DailyLogMontageBuilder.Layout 과 같은 규칙이다.
 * 한쪽만 바꾸면 영상이 붙는 순간 배치가 튄다.
 *
 * 영상이 언제 생기나: 그 날 전원이 목표를 채우면 즉시(완료 이벤트), 아니면 지난 날로
 * 넘어간 뒤 마감 배치가 훑어서 만든다. 그래서 `completedCount` 가 0 인 날도 나중에
 * 영상이 붙는다 — `videoUrl` 을 먼저 보고, 없을 때만 placeholder 로 내려간다.
 */
export function DailyLogTile({ log, onPlay }: Props) {
  const grid = tileGrid(log.totalCount);

  if (log.videoUrl) {
    return <DailyLogVideoTile log={log} grid={grid} onPlay={onPlay} />;
  }

  // row 가 있으면 그 날 인증은 최소 1건 있었다. 0 은 "아무도 안 했다" 가 아니라
  // "목표 횟수를 채운 사람이 없다" 는 뜻이라, 체크 칸이 하나도 없는 그리드 대신 문구를 낸다.
  if (log.completedCount === 0) {
    return (
      <div className="flex w-full items-center justify-center rounded-lg bg-gray-50 py-8">
        <p className="text-[13px] text-gray-400">
          이날은 목표를 채운 사람이 없어요
        </p>
      </div>
    );
  }

  return <PlaceholderGrid log={log} grid={grid} />;
}

/**
 * 합성 영상 타일.
 *
 * `videoUrl` 은 인증이 필요한 엔드포인트라 `<video src>` 로 직접 걸면 401 이다
 * (`<img>` 와 같은 이유 — 브라우저 요청에 Authorization 헤더가 안 실린다).
 * 그래서 `useAuthedImage` 로 fetch 해 objectURL 로 바꿔 쓴다. 이름은 image 지만
 * 내용은 content-type 을 가리지 않는 공용 blob 로더다.
 *
 * 받는 시점은 뷰포트 근처로 미룬다. 한 달치가 31칸이라 마운트에서 전부 받으면
 * mp4 31개를 동시에 내려받는다. 받히기 전에는 placeholder 가 자리를 지킨다.
 * 화면에 보이는 동안만 재생한다(리스트 동시재생 방지).
 */
function DailyLogVideoTile({
  log,
  grid,
  onPlay,
}: {
  log: DailyLog;
  grid: TileGrid;
  onPlay: (src: string) => void;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  // IntersectionObserver 가 없는 환경(테스트 등)에서는 지연을 포기하고 바로 받는다.
  const [near, setNear] = useState(
    () => typeof IntersectionObserver === "undefined",
  );

  useEffect(() => {
    if (near) return;
    const el = rootRef.current;
    if (!el) return;

    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setNear(true);
          io.disconnect();
        }
      },
      { rootMargin: "300px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [near]);

  const { url } = useAuthedImage(log.videoUrl, near);
  const videoRef = useAutoPlayInView();

  // 아직 안 받았거나 실패 — placeholder 가 ref 를 들고 자리를 지킨다.
  // url 이 생긴 뒤에는 near 가 이미 true 라 위 effect 가 ref 를 다시 찾지 않는다.
  if (!url) {
    return <PlaceholderGrid log={log} grid={grid} rootRef={rootRef} />;
  }

  return (
    <button
      type="button"
      onClick={() => onPlay(url)}
      className={`relative block w-full overflow-hidden rounded-lg bg-black ${grid.aspect}`}
    >
      <video
        ref={videoRef}
        src={url}
        muted
        loop
        playsInline
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

/** 영상 자리를 대신하는 타일 배치. 완료 인원만큼 체크 표시, 나머지는 검정. */
function PlaceholderGrid({
  log,
  grid,
  rootRef,
}: {
  log: DailyLog;
  grid: TileGrid;
  rootRef?: Ref<HTMLDivElement>;
}) {
  return (
    <div
      ref={rootRef}
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

interface TileGrid {
  cols: number;
  slots: number;
  aspect: string;
}

/**
 * 참여 인원 -> 타일 그리드. 각 칸은 정사각(사진 비율 유지, object-cover 로 크롭),
 * 컨테이너 비율만 인원수에 따라 달라진다.
 *   1 -> 1칸 정사각, 2와 3 -> 가로 한 줄, 4 -> 2x2, 5와 6 -> 3x2 (5는 마지막 칸 검정)
 * 서버 상한도 6칸이다(DailyLogMontageBuilder.MAX_CELLS).
 */
function tileGrid(totalCount: number): TileGrid {
  const n = Math.min(Math.max(totalCount || 1, 1), 6);
  if (n === 1) return { cols: 1, slots: 1, aspect: "aspect-square" };
  if (n === 2) return { cols: 2, slots: 2, aspect: "aspect-[2/1]" };
  if (n === 3) return { cols: 3, slots: 3, aspect: "aspect-[3/1]" };
  if (n === 4) return { cols: 2, slots: 4, aspect: "aspect-square" };
  return { cols: 3, slots: 6, aspect: "aspect-[3/2]" };
}

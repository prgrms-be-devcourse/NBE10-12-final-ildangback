import { XIcon } from "@phosphor-icons/react";
import { useEffect } from "react";
import { useBodyScrollLock } from "../../../shared/lib/useBodyScrollLock";

interface Props {
  src: string;
  onClose: () => void;
}

/** 일일 로그 영상 풀스크린 재생. 닫기: ✕ · 배경 탭 · Esc · 하드웨어 뒤로가기. */
export function DailyLogPlayer({ src, onClose }: Props) {
  useBodyScrollLock(true);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const onPop = () => onClose();
    window.addEventListener("keydown", onKey);
    window.history.pushState({ dailyLogPlayer: true }, "");
    window.addEventListener("popstate", onPop);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("popstate", onPop);
      if (window.history.state?.dailyLogPlayer) window.history.back();
    };
  }, [onClose]);

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="일일 로그 영상"
      onClick={onClose}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
    >
      <button
        type="button"
        aria-label="닫기"
        onClick={onClose}
        className="absolute top-3 right-3 flex size-9 items-center justify-center rounded-full bg-white/10 text-white"
      >
        <XIcon size={20} weight="bold" />
      </button>
      <video
        src={src}
        controls
        autoPlay
        onClick={(e) => e.stopPropagation()}
        className="max-h-full w-full max-w-[430px]"
      />
    </div>
  );
}

import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
} from "react";
import { useBodyScrollLock } from "../lib/useBodyScrollLock";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  /** 시트 상단 가운데 굵게 들어가는 제목. 생략 가능. */
  title?: string;
  children: ReactNode;
}

/** 이만큼 아래로 끌어내리면 닫는다. */
const DRAG_CLOSE_THRESHOLD = 64;
/** 이 이상 움직였으면 탭이 아니라 드래그로 본다. */
const DRAG_INTENT_PX = 6;

/**
 * 화면 아래에서 올라오는 시트. 닫기: 배경 탭 · Esc · 그래버 클릭 · 그래버 드래그다운.
 * 하드웨어 백은 추후.
 */
export function BottomSheet({ isOpen, onClose, title, children }: Props) {
  const titleId = useId();
  const [dragY, setDragY] = useState(0);
  const dragStart = useRef<number | null>(null);
  const didDrag = useRef(false);

  useBodyScrollLock(isOpen);

  const close = useCallback(() => {
    setDragY(0);
    onClose();
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [isOpen, close]);

  if (!isOpen) return null;

  function onPointerDown(e: ReactPointerEvent) {
    dragStart.current = e.clientY;
    didDrag.current = false;
    setDragY(0);
    e.currentTarget.setPointerCapture?.(e.pointerId);
  }

  function onPointerMove(e: ReactPointerEvent) {
    if (dragStart.current === null) return;
    const delta = Math.max(0, e.clientY - dragStart.current);
    if (delta > DRAG_INTENT_PX) didDrag.current = true;
    setDragY(delta);
  }

  function onPointerUp() {
    if (dragStart.current === null) return;
    const dragged = dragY;
    dragStart.current = null;
    if (dragged > DRAG_CLOSE_THRESHOLD) close();
    else setDragY(0);
  }

  function onGrabberClick() {
    // 드래그 끝에 딸려오는 click 은 무시한다. 순수 탭일 때만 닫는다.
    if (didDrag.current) {
      didDrag.current = false;
      return;
    }
    close();
  }

  return (
    <div className="fixed inset-0 z-50 flex flex-col justify-end">
      <div
        data-testid="bottomsheet-backdrop"
        onClick={close}
        className="absolute inset-0 bg-black/40"
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={title ? titleId : undefined}
        aria-label={title ? undefined : "바텀시트"}
        style={{ transform: dragY ? `translateY(${dragY}px)` : undefined }}
        className="relative mx-auto w-full max-w-[430px] rounded-t-3xl bg-white pb-[max(1.5rem,env(safe-area-inset-bottom))] shadow-[0_-8px_32px_rgba(0,0,0,0.12)]"
      >
        <button
          type="button"
          onClick={onGrabberClick}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          aria-label="닫기"
          className="mx-auto mt-3 block h-1.5 w-10 touch-none rounded-full bg-gray-300"
        />

        {title && (
          <h2
            id={titleId}
            className="mt-5 text-center text-[22px] font-bold text-gray-900"
          >
            {title}
          </h2>
        )}

        <div className="px-5 pt-4">{children}</div>
      </div>
    </div>
  );
}

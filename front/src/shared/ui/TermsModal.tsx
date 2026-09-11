import { XIcon } from "@phosphor-icons/react";
import { useEffect, useRef } from "react";
import { useBodyScrollLock } from "../lib/useBodyScrollLock";
import { Button } from "./Button";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  content: string;
}

/**
 * 네이티브 <dialog> + showModal 을 쓴다. Esc 닫기 · 포커스 트랩(바깥 inert) ·
 * 닫힌 뒤 열었던 버튼으로 포커스 복귀를 브라우저가 전부 해 준다 — 손으로 짜지 않는다.
 */
export function TermsModal({ isOpen, onClose, title, content }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useBodyScrollLock(isOpen);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (isOpen) {
      // StrictMode 가 effect 를 두 번 돌린다. 이미 열린 모달에 showModal 을 또 부르면 던진다.
      if (dialog && !dialog.open) dialog.showModal();
    } else {
      dialog?.close();
    }
  }, [isOpen]);

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="terms-modal-title"
      // Esc 가 dialog 를 직접 닫는 경로. React 상태를 따라오게 한다.
      onClose={onClose}
      // 패널 밖(::backdrop) 클릭은 dialog 자신이 target 이 된다. 안쪽 클릭은 자식이 target 이라 안 닫힌다.
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      // preflight 가 margin 을 지우므로 m-auto 로 가운데 정렬을 되살린다.
      className="m-auto w-[calc(100%-2rem)] max-w-[380px] rounded-2xl bg-white shadow-2xl backdrop:bg-black/50 backdrop:backdrop-blur-xs"
    >
      <div className="flex max-h-[80dvh] flex-col p-5">
        <div className="flex items-center justify-between pb-3 border-b border-gray-100">
          <h3
            id="terms-modal-title"
            className="text-[17px] font-bold text-gray-900"
          >
            {title}
          </h3>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700"
            aria-label="닫기"
          >
            <XIcon size={20} />
          </button>
        </div>

        <div className="my-4 flex-1 overflow-y-auto pr-1 text-[13px] leading-relaxed text-gray-600 whitespace-pre-line">
          {content}
        </div>

        <Button type="button" onClick={onClose} className="h-11 text-[15px]">
          확인
        </Button>
      </div>
    </dialog>
  );
}

import { XIcon } from "@phosphor-icons/react";
import { useEffect } from "react";
import { Button } from "./Button";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  content: string;
}

export function TermsModal({ isOpen, onClose, title, content }: Props) {
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
    } else {
      document.body.style.overflow = "";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="terms-modal-title"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-xs"
      onClick={onClose}
    >
      <div
        className="flex max-h-[80dvh] w-full max-w-[380px] flex-col rounded-2xl bg-white p-5 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
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
    </div>
  );
}

import { XIcon } from "@phosphor-icons/react";
import { useEffect } from "react";
import { formatDateTimeMinute } from "../../../shared/lib/date";
import { useBodyScrollLock } from "../../../shared/lib/useBodyScrollLock";
import { badgeColor, nicknameInitial } from "../lib/authorBadge";
import type { CheckIn } from "../types";

interface Props {
  /** null 이면 닫힘. */
  item: CheckIn | null;
  onClose: () => void;
  /** 작성자 한 줄 표시. 갤러리 탭만. */
  showAuthor?: boolean;
}

/**
 * 인증 사진 확대 뷰. 전체화면 어두운 오버레이 + 큰 정사각 이미지, 아래에 시간과 memo.
 * 닫기: ✕ 버튼 · 배경 탭 · Esc · 하드웨어 뒤로가기.
 */
export function CheckInLightbox({ item, onClose, showAuthor = false }: Props) {
  const open = item !== null;
  useBodyScrollLock(open);

  useEffect(() => {
    if (!open) return;

    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    const onPop = () => onClose();

    window.addEventListener("keydown", onKey);
    window.history.pushState({ lightbox: true }, "");
    window.addEventListener("popstate", onPop);

    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener("popstate", onPop);
      // 뒤로가기가 아닌 경로(✕·배경)로 닫혔으면 pushState 한 항목을 소비한다.
      if (window.history.state?.lightbox) window.history.back();
    };
  }, [open, onClose]);

  if (!item) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="인증 사진"
      onClick={onClose}
      className="fixed inset-0 z-50 flex flex-col overflow-y-auto bg-black/90"
    >
      <button
        type="button"
        aria-label="닫기"
        onClick={onClose}
        className="absolute top-3 right-3 z-10 flex size-9 items-center justify-center rounded-full bg-white/10 text-white"
      >
        <XIcon size={20} weight="bold" />
      </button>

      <div
        onClick={(e) => e.stopPropagation()}
        className="m-auto w-full max-w-[430px] px-4 py-14"
      >
        <img
          src={item.mediaUrl}
          alt={`${item.nickname}의 인증`}
          className="w-full rounded-xl object-contain"
        />

        <div className="mt-4 text-white">
          {showAuthor && (
            <div className="mb-2 flex items-center gap-2">
              <span
                className="flex size-7 items-center justify-center rounded-full text-[12px] font-bold"
                style={{ backgroundColor: badgeColor(item.userId) }}
              >
                {nicknameInitial(item.nickname, 2)}
              </span>
              <span className="text-[14px] font-semibold">{item.nickname}</span>
            </div>
          )}
          <p className="text-[13px] text-white/60">
            {formatDateTimeMinute(item.createdAt)}
          </p>
          {item.memo && (
            <p className="mt-2 text-[15px] leading-relaxed whitespace-pre-wrap">
              {item.memo}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}

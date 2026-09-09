import { useEffect, useRef } from "react";
import { ITEM_ART } from "../../../mocks/itemArt";
import { objectParticle } from "../../../shared/lib/korean";
import type { ShopItem } from "../../../mocks/types";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { pixelIcons } from "../../../shared/ui/pixelIcons";

const DEEP = "#774AD3";
const BUBBLE = "#EBE3F6";

/**
 * 구매 전 확인. 포인트는 되돌릴 수 없이 빠져나가므로 한 번 묻는다.
 *
 * `TermsModal` 과 같은 네이티브 <dialog> 방식이다 — Esc 닫기 · 포커스 트랩 ·
 * 닫힌 뒤 열었던 버튼으로 포커스 복귀를 브라우저가 해 준다.
 */
export function PurchaseDialog({
  item,
  point,
  busy,
  onConfirm,
  onClose,
}: {
  item: ShopItem | null;
  point: number;
  busy: boolean;
  onConfirm(): void;
  onClose(): void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const isOpen = item !== null;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (isOpen) {
      // StrictMode 가 effect 를 두 번 돌린다. 이미 열린 모달에 또 부르면 던진다.
      if (dialog && !dialog.open) dialog.showModal();
    } else {
      dialog?.close();
    }
  }, [isOpen]);

  if (!item) return null;

  const rest = point - item.price;

  return (
    <dialog
      ref={dialogRef}
      aria-labelledby="purchase-dialog-title"
      onClose={onClose}
      onClick={(e) => {
        // 구매 중에는 바깥을 눌러도 닫지 않는다. 결과를 못 보게 된다.
        if (e.target === e.currentTarget && !busy) onClose();
      }}
      className="m-auto w-[calc(100%-3rem)] max-w-[320px] rounded-[14px] bg-white shadow-2xl backdrop:bg-black/50"
    >
      <div className="p-[20px] text-center">
        <span className="mx-auto flex h-[76px] w-[76px] items-center justify-center rounded-[14px] bg-purple-50">
          {ITEM_ART[item.art] ? (
            <img
              src={ITEM_ART[item.art]}
              alt=""
              className="max-h-[56px] max-w-[56px] object-contain pixelated"
            />
          ) : (
            <span className="text-[36px] leading-none" aria-hidden>
              {item.art}
            </span>
          )}
        </span>

        <h2
          id="purchase-dialog-title"
          className="mt-[14px] text-[16px] font-bold text-gray-900"
        >
          {item.name}
          <span className="font-medium">
            {objectParticle(item.name)} 구매할까요?
          </span>
        </h2>

        <dl
          className="mt-[16px] rounded-[10px] px-[14px] py-[12px] text-[12px]"
          style={{ backgroundColor: BUBBLE }}
        >
          <div className="flex items-center justify-between">
            <dt className="text-gray-500">가격</dt>
            <dd className="font-bold tabular-nums" style={{ color: DEEP }}>
              {item.price.toLocaleString()}P
            </dd>
          </div>
          <div className="mt-[8px] flex items-center justify-between">
            <dt className="text-gray-500">구매 후 남는 포인트</dt>
            <dd className="font-bold text-gray-900 tabular-nums">
              {rest.toLocaleString()}P
            </dd>
          </div>
        </dl>

        <p className="mt-[8px] text-[11px] text-gray-500">
          구매하면 바로 착용돼요
        </p>

        <div className="mt-[14px] flex gap-[8px]">
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="h-[44px] flex-1 rounded-[10px] border border-purple-200 text-[14px] font-semibold text-gray-500 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:opacity-40"
          >
            취소
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="h-[44px] flex-1 rounded-[10px] text-[14px] font-semibold text-white focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:opacity-60"
            style={{ backgroundColor: DEEP }}
          >
            {busy ? "구매 중…" : "구매하기"}
          </button>
        </div>

        <p className="mt-[10px] flex items-center justify-center gap-[4px] text-[11px] text-gray-500">
          <PixelIcon src={pixelIcons.pointHistory} size={13} />
          보유 {point.toLocaleString()}P
        </p>
      </div>
    </dialog>
  );
}

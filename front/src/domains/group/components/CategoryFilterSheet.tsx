import { useEffect, useRef, useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { CATEGORIES, CATEGORY_LABEL } from "../constants";
import type { GroupCategory } from "../types";

export function CategoryFilterSheet({
  value,
  onApply,
  onClose,
}: {
  value?: GroupCategory;
  onApply: (value?: GroupCategory) => void;
  onClose: () => void;
}) {
  const [selected, setSelected] = useState(value);
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog && !dialog.open) dialog.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      dialog?.close();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-labelledby="category-title"
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      className="fixed inset-x-0 top-auto bottom-0 mx-auto max-h-[85dvh] w-full max-w-[430px] overflow-y-auto rounded-t-3xl bg-white px-6 pt-5 pb-[max(2rem,env(safe-area-inset-bottom))] backdrop:bg-black/40"
    >
      <div className="mx-auto mb-6 h-1.5 w-16 rounded-full bg-purple-500" />
      <div className="flex items-center justify-between">
        <h2 id="category-title" className="text-xl font-bold">
          카테고리 필터
        </h2>
        <button
          type="button"
          onClick={() => setSelected(undefined)}
          className="p-2 text-sm text-purple-500"
        >
          초기화
        </button>
      </div>
      <p className="mt-2 text-sm text-gray-500">
        관심 있는 카테고리를 선택해보세요
      </p>
      <div className="my-7 grid grid-cols-3 gap-2">
        {([undefined, ...CATEGORIES] as const).map((category) => (
          <button
            key={category ?? "all"}
            type="button"
            aria-pressed={selected === category}
            onClick={() => setSelected(category)}
            className={`min-h-12 rounded-xl border text-sm font-semibold ${selected === category ? "border-purple-500 bg-purple-500 text-white" : "border-purple-200 text-gray-500"}`}
          >
            {category ? CATEGORY_LABEL[category] : "전체"}
          </button>
        ))}
      </div>
      <Button
        onClick={() => {
          onApply(selected);
          onClose();
        }}
      >
        그룹 보기
      </Button>
      <button
        type="button"
        onClick={onClose}
        className="mt-3 w-full py-2 text-sm text-gray-500"
      >
        닫기
      </button>
    </dialog>
  );
}

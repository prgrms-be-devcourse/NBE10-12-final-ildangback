import type { ReactNode } from "react";

interface Props {
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

/** 필터 칩. 갤러리 참여자 칩 · 앨범 유형 칩이 공유. */
export function Chip({ active, onClick, disabled = false, children }: Props) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`shrink-0 rounded-full px-3 py-1 text-[13px] font-medium transition-colors ${
        active
          ? "bg-purple-500 text-white"
          : "bg-gray-100 text-gray-500 disabled:text-gray-300"
      }`}
    >
      {children}
    </button>
  );
}

import type { ReactNode } from "react";
import { CaretLeftIcon } from "@phosphor-icons/react";
import { useNavigate } from "react-router";

export function TopBar({
  title,
  className = "",
  onBack,
}: {
  title?: ReactNode;
  className?: string;
  /** 기본은 브라우저 히스토리 back. 스텝 화면처럼 뒤로가기를 직접 다뤄야 할 때 넘긴다. */
  onBack?: () => void;
}) {
  const navigate = useNavigate();

  return (
    <header
      className={`relative flex h-14 items-center px-4 pt-3 ${className}`}
    >
      <button
        type="button"
        onClick={onBack ?? (() => navigate(-1))}
        aria-label="뒤로 가기"
        className="-ml-2 rounded-lg p-2 text-gray-900 transition-colors hover:bg-purple-50"
      >
        <CaretLeftIcon size={24} weight="bold" />
      </button>
      {title && (
        <div className="pointer-events-none absolute inset-x-0 flex justify-center text-[16px] font-bold text-gray-900">
          {title}
        </div>
      )}
    </header>
  );
}

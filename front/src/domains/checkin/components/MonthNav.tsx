import { CaretLeftIcon, CaretRightIcon } from "@phosphor-icons/react";
import { monthLabel, shiftMonth } from "../lib/month";

interface Props {
  /** yyyy-MM */
  month: string;
  onChange: (month: string) => void;
}

/** "◀ 2026년 8월 ▶" 월 네비게이션. 갤러리 탭 · 앨범 · 일일 로그가 공유. */
export function MonthNav({ month, onChange }: Props) {
  return (
    <div className="flex items-center justify-center gap-6">
      <button
        type="button"
        aria-label="이전 달"
        onClick={() => onChange(shiftMonth(month, -1))}
        className="text-gray-400 hover:text-gray-600"
      >
        <CaretLeftIcon size={20} weight="bold" />
      </button>
      <span className="text-[15px] font-bold text-gray-900">
        {monthLabel(month)}
      </span>
      <button
        type="button"
        aria-label="다음 달"
        onClick={() => onChange(shiftMonth(month, 1))}
        className="text-gray-400 hover:text-gray-600"
      >
        <CaretRightIcon size={20} weight="bold" />
      </button>
    </div>
  );
}

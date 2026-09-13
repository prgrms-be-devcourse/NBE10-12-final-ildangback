import { CaretDownIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";

interface Props {
  value: string;
  onChange: (value: string) => void;
  children: ReactNode;
  "aria-label": string;
}

/** 시안의 드롭다운. native select + 캐럿 아이콘. */
export function CheckInSelect({ value, onChange, children, ...rest }: Props) {
  return (
    <div className="relative">
      <select
        {...rest}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="appearance-none rounded-lg border border-gray-200 bg-white py-2 pr-8 pl-3 text-[13px] font-medium text-gray-700"
      >
        {children}
      </select>
      <CaretDownIcon
        size={14}
        weight="bold"
        className="pointer-events-none absolute top-1/2 right-2.5 -translate-y-1/2 text-gray-400"
      />
    </div>
  );
}

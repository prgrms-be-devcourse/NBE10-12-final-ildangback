import { CheckIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";

interface CheckboxProps {
  checked: boolean;
  onChange(checked: boolean): void;
  children: ReactNode;
  /** 전체 동의처럼 좀 더 굵게 보여야 하는 줄 */
  emphasized?: boolean;
}

export function Checkbox({
  checked,
  onChange,
  children,
  emphasized = false,
}: CheckboxProps) {
  return (
    <label className="flex cursor-pointer items-center gap-3 py-2">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="peer sr-only"
      />
      <span
        aria-hidden
        className={`flex size-5 shrink-0 items-center justify-center rounded-full border transition-colors peer-focus-visible:ring-2 peer-focus-visible:ring-purple-300 ${
          checked
            ? "border-purple-500 bg-purple-500 text-white"
            : "border-purple-200 bg-white text-transparent"
        }`}
      >
        <CheckIcon size={12} weight="bold" />
      </span>
      <span
        className={`text-[14px] ${emphasized ? "font-semibold text-gray-900" : "text-gray-500"}`}
      >
        {children}
      </span>
    </label>
  );
}

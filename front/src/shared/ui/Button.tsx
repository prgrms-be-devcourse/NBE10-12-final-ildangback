import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "ghost" | "danger";

const VARIANTS: Record<Variant, string> = {
  primary: "bg-purple-500 text-white hover:bg-purple-600 active:bg-purple-600",
  secondary:
    "border border-purple-200 bg-white text-purple-700 hover:bg-purple-50",
  ghost: "text-gray-500 hover:bg-gray-100",
  danger: "border border-red-200 bg-white text-red-600 hover:bg-red-50",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export function Button({
  variant = "primary",
  loading = false,
  disabled,
  className = "",
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`h-13 w-full rounded-xl text-[15px] font-semibold transition-colors focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40 ${VARIANTS[variant]} ${className}`}
    >
      {loading ? "처리 중…" : children}
    </button>
  );
}

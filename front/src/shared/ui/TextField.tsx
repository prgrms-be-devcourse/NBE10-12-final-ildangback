import { EyeIcon, EyeSlashIcon } from "@phosphor-icons/react";
import { forwardRef, useId, useState, type InputHTMLAttributes } from "react";

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  /** "12/20" 처럼 인풋 오른쪽 안에 글자 수를 띄운다. */
  counter?: string;
  /** 비밀번호 보기 토글을 붙인다. */
  revealable?: boolean;
  /** 라벨을 화면에서 숨기되 스크린리더에는 남긴다. 옆 맥락으로 뭔지 뻔한 단독 인풋에 쓴다. */
  hideLabel?: boolean;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  function TextField(
    {
      label,
      error,
      counter,
      revealable = false,
      hideLabel = false,
      type = "text",
      className = "",
      ...props
    },
    ref,
  ) {
    const id = useId();
    const [revealed, setRevealed] = useState(false);
    const inputType = revealable && revealed ? "text" : type;

    return (
      <div className={className}>
        <label
          htmlFor={id}
          className={
            hideLabel
              ? "sr-only"
              : "mb-2 block text-[13px] font-semibold text-gray-900"
          }
        >
          {label}
        </label>

        <div className="relative">
          <input
            {...props}
            id={id}
            ref={ref}
            type={inputType}
            aria-invalid={error ? true : undefined}
            aria-errormessage={error ? `${id}-error` : undefined}
            className={`h-13 w-full rounded-xl border bg-white px-4 text-gray-900 placeholder:text-gray-400 focus:ring-2 focus:outline-none ${
              error
                ? "border-red-400 focus:border-red-400 focus:ring-red-100"
                : "border-purple-200 focus:border-purple-500 focus:ring-purple-300"
            } ${revealable || counter ? "pr-12" : ""}`}
          />

          {revealable && (
            <button
              type="button"
              onClick={() => setRevealed((v) => !v)}
              aria-label={revealed ? "비밀번호 숨기기" : "비밀번호 보기"}
              className="absolute top-1/2 right-3 -translate-y-1/2 p-1 text-gray-500"
            >
              {revealed ? <EyeSlashIcon size={20} /> : <EyeIcon size={20} />}
            </button>
          )}

          {counter && !revealable && (
            <span className="absolute top-1/2 right-4 -translate-y-1/2 text-[13px] text-gray-500">
              {counter}
            </span>
          )}
        </div>

        {error && (
          <p
            id={`${id}-error`}
            role="alert"
            className="mt-1.5 text-[13px] text-red-600"
          >
            {error}
          </p>
        )}
      </div>
    );
  },
);

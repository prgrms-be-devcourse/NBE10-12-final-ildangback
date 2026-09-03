interface ToggleProps {
  checked: boolean;
  onChange(checked: boolean): void;
  label: string;
}

export function Toggle({ checked, onChange, label }: ToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none ${
        checked ? "bg-purple-500" : "bg-gray-200"
      }`}
    >
      <span
        className={`absolute top-0.5 size-5 rounded-full bg-white transition-all ${
          checked ? "left-[22px]" : "left-0.5"
        }`}
      />
    </button>
  );
}

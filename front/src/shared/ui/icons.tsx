// 선 굵기와 크기를 헤더의 phosphor 아이콘(CaretLeft · DotsThree)에 맞춰 그렸다.
// 색은 currentColor 라서 쓰는 자리의 text-* 를 따라간다.

export function ShopIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      <path d="M5 9h14l-1 9.6a2.4 2.4 0 0 1-2.39 2.15H8.39A2.4 2.4 0 0 1 6 18.6L5 9Z" />
      <path d="M9.25 11.5V7.75a2.75 2.75 0 0 1 5.5 0v3.75" />
    </svg>
  );
}
export function ChatIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden
      className={className}
    >
      <path d="M20.25 11.6c0 3.7-3.69 6.7-8.25 6.7-.83 0-1.63-.1-2.39-.28-.4.26-1.35.8-2.94 1.24-.31.09-.57-.22-.44-.51.3-.66.6-1.53.72-2.4C4.99 15.13 3.75 13.5 3.75 11.6c0-3.7 3.69-6.7 8.25-6.7s8.25 3 8.25 6.7Z" />
      <circle cx="8.6" cy="11.6" r="1.05" fill="currentColor" stroke="none" />
      <circle cx="12" cy="11.6" r="1.05" fill="currentColor" stroke="none" />
      <circle cx="15.4" cy="11.6" r="1.05" fill="currentColor" stroke="none" />
    </svg>
  );
}

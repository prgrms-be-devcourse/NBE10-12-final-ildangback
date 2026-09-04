export function LoadingScreen() {
  return (
    <div className="flex min-h-dvh items-center justify-center bg-white">
      <span
        role="status"
        aria-label="불러오는 중"
        className="size-8 animate-spin rounded-full border-3 border-purple-200 border-t-purple-500"
      />
    </div>
  );
}

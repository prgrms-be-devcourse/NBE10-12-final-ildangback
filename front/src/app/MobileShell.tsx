import type { ReactNode } from "react";

/**
 * 시안이 전부 모바일 고정폭이다. PC 브라우저로 열면 가운데 정렬하고 양옆은 배경으로 둔다.
 * 클라이언트 전략(PWA · 웹뷰 · TWA)이 아직 미정인데 이 형태는 셋 중 뭐가 되든 안 깨진다.
 */
export function MobileShell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-gray-100">
      <div className="relative flex min-h-dvh w-full max-w-[430px] flex-col bg-white">
        {children}
      </div>
    </div>
  );
}

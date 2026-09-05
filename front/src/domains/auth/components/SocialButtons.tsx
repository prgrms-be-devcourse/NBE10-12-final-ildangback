import { useEffect, useState } from "react";
import { useToast } from "../../../shared/lib/useToast";
import { startOAuth, type OAuthProviderId } from "../oauth";

function NaverIcon() {
  return (
    <svg className="size-4.5 fill-current" viewBox="0 0 24 24">
      <path d="M16.273 12.845L7.376 0H0v24h7.727V11.155L16.624 24H24V0h-7.727z" />
    </svg>
  );
}

function GoogleIcon() {
  return (
    <svg className="size-5" viewBox="0 0 24 24">
      <path
        fill="#4285F4"
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
      />
      <path
        fill="#34A853"
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
      />
      <path
        fill="#FBBC05"
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
      />
      <path
        fill="#EA4335"
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
      />
    </svg>
  );
}

export function SocialButtons() {
  const { showToast } = useToast();
  // 어느 버튼을 눌렀는지까지 들고 있어야 그 버튼만 이동 중으로 바꿀 수 있다.
  const [leaving, setLeaving] = useState<OAuthProviderId | null>(null);

  // 프로바이더 화면에서 뒤로가기로 돌아오면 브라우저가 페이지를 통째로 되살린다(bfcache).
  // 나가면서 잠가둔 상태까지 같이 살아나서, 풀어주지 않으면 버튼이 계속 눌리지 않는다.
  useEffect(() => {
    const restore = (event: PageTransitionEvent) => {
      if (event.persisted) setLeaving(null);
    };
    window.addEventListener("pageshow", restore);
    return () => window.removeEventListener("pageshow", restore);
  }, []);

  const go = async (provider: OAuthProviderId) => {
    setLeaving(provider);
    try {
      await startOAuth(provider);
    } catch {
      // 클라이언트 ID 가 비었거나 crypto.subtle 이 없는 경우다.
      // 후자는 https 나 localhost 가 아닌 주소로 열었을 때 생긴다.
      setLeaving(null);
      showToast("소셜 로그인을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  return (
    <section className="mt-8" aria-label="소셜 로그인">
      <div className="flex items-center gap-3">
        <span className="h-px flex-1 bg-purple-200" />
        <span className="text-[12px] text-gray-500">또는</span>
        <span className="h-px flex-1 bg-purple-200" />
      </div>

      <div className="mt-5 flex flex-col gap-3">
        <button
          type="button"
          disabled={leaving !== null}
          onClick={() => go("naver")}
          className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-[#03C75A] text-[15px] font-semibold text-white transition-opacity hover:opacity-95 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:opacity-40"
        >
          <NaverIcon />
          {leaving === "naver" ? "네이버로 이동 중…" : "네이버로 계속하기"}
        </button>

        <button
          type="button"
          disabled={leaving !== null}
          onClick={() => go("google")}
          className="flex h-12 w-full items-center justify-center gap-2 rounded-xl border border-purple-200 bg-white text-[15px] font-semibold text-gray-900 transition-colors hover:bg-gray-50 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:opacity-40"
        >
          <GoogleIcon />
          {leaving === "google" ? "Google로 이동 중…" : "Google로 계속하기"}
        </button>
      </div>
    </section>
  );
}

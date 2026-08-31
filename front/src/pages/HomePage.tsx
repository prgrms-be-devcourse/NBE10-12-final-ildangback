import { useNavigate } from "react-router";
import homeChangesCards from "../assets/illustrations/home-changes-cards.webp";
import homeHeroCard from "../assets/illustrations/home-hero-card.webp";
import { useAuth } from "../shared/lib/useAuth";
import { Logo } from "../shared/ui/Logo";
import { PageHeader } from "../shared/ui/PageHeader";

export function HomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  // 로그인 후의 홈은 이번 이슈(#13 user 도메인) 범위가 아니다. 자리만 잡아 둔다.
  if (user) {
    return (
      <>
        <PageHeader title="홈" />
        <div className="px-6 py-20 text-center text-[14px] text-gray-500">
          홈 화면이 들어올 자리입니다.
        </div>
      </>
    );
  }

  return (
    <div className="px-5 pt-5 pb-10">
      <Logo className="h-14" />

      <div
        role="button"
        tabIndex={0}
        onClick={() => navigate("/signup")}
        onKeyDown={(e) => e.key === "Enter" && navigate("/signup")}
        className="mt-5 w-full cursor-pointer overflow-hidden rounded-2xl transition-transform active:scale-[0.99]"
      >
        <img
          src={homeHeroCard}
          alt="함께 만드는 작은 습관 - 함께 힘을 모으면 꾸준해져요 - 꼬밋 시작하기"
          className="w-full object-contain pixelated"
        />
      </div>

      <section className="mt-7">
        <h3 className="text-[16px] font-semibold text-gray-900">
          꼬밋에서 만나는 변화
        </h3>

        <div className="mt-3 w-full overflow-hidden rounded-2xl">
          <img
            src={homeChangesCards}
            alt="오늘을 인증하고, 기록을 차곡차곡 키우고, 한 달을 함께 완성해요"
            className="w-full object-contain pixelated"
          />
        </div>
      </section>
    </div>
  );
}

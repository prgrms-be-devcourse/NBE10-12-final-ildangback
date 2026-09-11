import challengeHero from "../assets/illustrations/challenge-hero.webp";
import iconBook from "../assets/icons/book-purple.webp";
import iconCamera from "../assets/icons/camera-purple.webp";
import iconTrophy from "../assets/icons/trophy-purple.webp";
import { useAuth } from "../shared/lib/useAuth";
import { PageHeader } from "../shared/ui/PageHeader";
import { SignUpPrompt } from "../shared/ui/SignUpPrompt";

export function ChallengeTabPlaceholder() {
  const { user } = useAuth();

  return (
    <>
      <PageHeader title="챌린지" />

      {user ? (
        <div className="px-6 py-20 text-center text-[14px] text-gray-500">
          챌린지 도메인 화면이 들어올 자리입니다.
        </div>
      ) : (
        <SignUpPrompt
          title="함께할 챌린지를 시작해보세요"
          description={
            "회원가입하면 챌린지에 참여하고\n매일 인증을 남길 수 있어요"
          }
          illustration={
            <img
              src={challengeHero}
              alt="챌린지 일러스트"
              className="h-36 object-contain pixelated"
            />
          }
          features={[
            { icon: iconTrophy, label: "원하는 챌린지 참여" },
            { icon: iconCamera, label: "사진 · 영상 · 라이브 인증" },
            { icon: iconBook, label: "그룹원과 함께 성장하기" },
          ]}
        />
      )}
    </>
  );
}

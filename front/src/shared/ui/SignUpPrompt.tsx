import { Link, useNavigate } from "react-router";
import { Button } from "./Button";

export interface PromptFeature {
  icon: string | React.ComponentType<{ size?: number; className?: string }>;
  label: string;
}

interface Props {
  title: string;
  description: string;
  features: PromptFeature[];
  illustration?: React.ReactNode;
  cardBg?: string;
}

/** 비로그인으로 탭에 들어왔을 때의 빈 상태 + 가입 유도. 챌린지 · 프로필 탭이 같은 모양이다. */
export function SignUpPrompt({
  title,
  description,
  features,
  illustration,
  cardBg = "bg-white",
}: Props) {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center px-6 pt-4 pb-10">
      <div className="flex h-56 w-full items-center justify-center py-1">
        {illustration}
      </div>

      <h2 className="mt-4 text-center text-[28px] font-bold leading-tight text-gray-900">
        {title}
      </h2>
      <p className="mt-2.5 text-center text-[15px] leading-relaxed whitespace-pre-line text-gray-500">
        {description}
      </p>

      <ul
        className={`mt-7 w-full max-w-[320px] mx-auto rounded-2xl border border-purple-200 ${cardBg} px-5 py-1.5 shadow-xs`}
      >
        {features.map(({ icon: IconOrSrc, label }, index) => (
          <li
            key={label}
            className={`flex items-center gap-4 py-4 ${index > 0 ? "border-t border-purple-100" : ""}`}
          >
            {typeof IconOrSrc === "string" ? (
              <img
                src={IconOrSrc}
                alt=""
                className="h-12 w-12 object-contain pixelated shrink-0"
              />
            ) : (
              <IconOrSrc size={36} className="shrink-0 text-purple-500" />
            )}
            <span className="text-[17px] font-semibold text-gray-900">
              {label}
            </span>
          </li>
        ))}
      </ul>

      <Button
        onClick={() => navigate("/signup")}
        className="mt-8 h-13 text-[16px]"
      >
        회원가입하고 시작하기
      </Button>

      <p className="mt-4 text-[14px] text-gray-500">
        이미 계정이 있나요?{" "}
        <Link to="/login" className="font-semibold text-purple-500 underline">
          로그인
        </Link>
      </p>
    </div>
  );
}

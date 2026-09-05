import { useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { Logo } from "../../../shared/ui/Logo";
import { getMyProfile } from "../../user/api";

/**
 * 인증 메일 링크의 종착지.
 *
 * 링크는 백엔드를 가리키고 백엔드가 판정한 뒤 여기로 302 한다. 프론트는 status 만 읽는다 —
 * 토큰을 만질 일이 없고, 만질 수도 없다.
 */
export function VerifyResultPage() {
  const [params] = useSearchParams();
  const { status: authStatus, replaceUser } = useAuth();
  const navigate = useNavigate();
  const verified = params.get("status") === "success";

  // 인증 전에 받아둔 프로필은 emailVerified 가 false 다. 로그인 상태로 이 화면에 왔으면
  // 다시 받아와야 다른 화면의 안내 칸이 사라진다.
  useEffect(() => {
    if (!verified || authStatus !== "authenticated") return;

    let cancelled = false;
    getMyProfile()
      .then((profile) => {
        if (!cancelled) replaceUser(profile);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [verified, authStatus, replaceUser]);

  return (
    <div className="flex flex-1 flex-col justify-center px-6 pb-10">
      <div className="flex justify-center">
        <Logo className="h-14" />
      </div>

      <h1 className="mt-8 text-center text-[24px] font-bold text-balance text-gray-900">
        {verified ? "이메일 인증이 끝났어요" : "인증 링크를 쓸 수 없어요"}
      </h1>
      <p className="mt-3 text-center text-[14px] leading-relaxed text-gray-500">
        {verified
          ? "이제 비밀번호를 잊어도 재설정으로 되찾을 수 있어요."
          : "링크가 만료됐거나 이미 사용됐어요. 계정 관리에서 인증 메일을 다시 받아주세요."}
      </p>

      <div className="mt-10 flex flex-col gap-3">
        {authStatus === "authenticated" ? (
          <Button onClick={() => navigate("/", { replace: true })}>
            홈으로
          </Button>
        ) : (
          <Button onClick={() => navigate("/login", { replace: true })}>
            로그인하러 가기
          </Button>
        )}
      </div>
    </div>
  );
}

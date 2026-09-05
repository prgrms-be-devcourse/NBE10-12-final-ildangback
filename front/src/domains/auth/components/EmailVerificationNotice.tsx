import { useEffect, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { pixelIcons } from "../../../shared/ui/pixelIcons";
import { PixelIcon } from "../../../shared/ui/PixelIcon";
import { getMyProfile } from "../../user/api";
import { resendVerificationEmail } from "../api";

/** 서버의 최소 발송 간격과 같은 값이다. 다르면 눌러도 429 만 받는다. */
const RESEND_INTERVAL_SEC = 60;

/**
 * 미인증 계정에만 뜨는 안내와 재발송 버튼.
 *
 * 인증하지 않아도 로그인과 이용은 막히지 않는다. 막히는 것은 비밀번호 재설정뿐이라
 * 화면을 가리지 않고 한 칸으로만 알린다.
 */
export function EmailVerificationNotice() {
  const { user, replaceUser } = useAuth();
  const { showToast } = useToast();
  const [sending, setSending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((sec) => sec - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  // 비밀번호가 없으면 재설정할 것도 없어서 인증이 지키는 문이 없다.
  // 소셜로만 가입한 사람에게 인증을 권할 이유가 없다.
  if (!user || user.emailVerified || !user.hasPassword) return null;

  const resend = async () => {
    setSending(true);
    try {
      await resendVerificationEmail();
      setCooldown(RESEND_INTERVAL_SEC);
      showToast("인증 메일을 보냈어요. 메일함을 확인해 주세요.");
    } catch (error) {
      if (!(error instanceof ApiError)) {
        showToast("네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
        return;
      }
      showToast(error.message);
      // 아직 최소 간격 안이다. 다시 눌러도 같은 응답이라 버튼을 잠가둔다.
      if (error.code === "EMAIL_RESEND_TOO_SOON")
        setCooldown(RESEND_INTERVAL_SEC);
      // 다른 탭에서 이미 인증을 끝낸 경우다. 최신 프로필을 받아오면 이 칸이 사라진다.
      if (error.code === "EMAIL_ALREADY_VERIFIED") {
        await getMyProfile()
          .then(replaceUser)
          .catch(() => undefined);
      }
    } finally {
      setSending(false);
    }
  };

  const waiting = cooldown > 0;

  return (
    <section className="mt-3 flex gap-3 rounded-2xl border border-purple-200 bg-white px-4 py-4">
      <PixelIcon src={pixelIcons.verificationNotification} size={28} />

      <div className="min-w-0 flex-1">
        <p className="text-[15px] font-bold text-balance text-gray-900">
          이메일 인증이 남았어요
        </p>
        <p className="mt-1 text-[13px] leading-relaxed text-gray-500">
          인증을 마쳐야 비밀번호를 잊었을 때 되찾을 수 있어요.
        </p>

        <button
          type="button"
          onClick={resend}
          disabled={sending || waiting}
          className="mt-3 h-9 rounded-lg bg-purple-500 px-4 text-[13px] font-semibold text-white transition-colors hover:bg-purple-600 focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-purple-200"
        >
          {sending
            ? "보내는 중…"
            : waiting
              ? `${cooldown}초 후 다시 보낼 수 있어요`
              : "인증 메일 받기"}
        </button>
      </div>
    </section>
  );
}

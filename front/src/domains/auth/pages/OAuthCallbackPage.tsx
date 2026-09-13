import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { ApiError } from "../../../shared/api/client";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { LoadingScreen } from "../../../shared/ui/LoadingScreen";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  appCallbackUrl,
  isAppState,
  isAppWebView,
  isOAuthProvider,
  redirectUriOf,
  takePendingOAuth,
} from "../oauth";

const CANCELLED = "로그인을 취소했어요.";
const BROKEN_REQUEST =
  "로그인 요청이 올바르지 않아요. 처음부터 다시 시도해 주세요.";

/** 서버에 보내기도 전에 콜백이 어긋난 경우. 화면에 띄울 문구를 그대로 들고 간다. */
class CallbackError extends Error {}

/**
 * 프로바이더가 인가 코드를 들고 돌아오는 곳.
 *
 * state 대조가 여기서 끝난다 — 백엔드는 state 를 검증하지 않으므로 이 비교를 빼면
 * CSRF 방어가 통째로 사라진다.
 */
export function OAuthCallbackPage() {
  const { provider } = useParams();
  const [params] = useSearchParams();
  const { signInWithOAuth } = useAuth();
  const navigate = useNavigate();
  const [failure, setFailure] = useState<string | null>(null);

  // 앱에서 시작한 로그인인데 여기가 웹뷰가 아니면 Custom Tab 안이다.
  // 대조에 쓸 pending 은 웹뷰의 sessionStorage 에 있어서 이쪽에는 없다.
  // 손대지 말고 쿼리 그대로 앱에 넘긴다 — docs/앱설계.md 4-1.
  //
  // UA 조건을 빼면 웹뷰가 자기가 받은 콜백을 또 튕겨서 무한 루프가 된다.
  const handoff =
    !isAppWebView() &&
    isAppState(params.get("state")) &&
    provider !== undefined &&
    isOAuthProvider(provider)
      ? appCallbackUrl(provider, window.location.search)
      : null;

  // 인가 코드는 1회용인데 StrictMode 는 개발 모드에서 effect 를 두 번 돌린다.
  // 두 번째 실행이 같은 코드를 다시 보내면 프로바이더가 거절한다.
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    if (handoff) {
      window.location.replace(handoff);
      return;
    }

    const exchange = async () => {
      const pending = takePendingOAuth();
      const code = params.get("code");
      const state = params.get("state");

      if (params.get("error")) throw new CallbackError(CANCELLED);
      if (!provider || !isOAuthProvider(provider) || !code || !state) {
        throw new CallbackError(BROKEN_REQUEST);
      }
      if (
        !pending ||
        pending.provider !== provider ||
        pending.state !== state
      ) {
        throw new CallbackError(BROKEN_REQUEST);
      }

      return signInWithOAuth(provider, {
        code,
        state,
        redirectUri: redirectUriOf(provider),
        codeVerifier: pending.codeVerifier,
      });
    };

    exchange()
      .then((newUser) => {
        if (!newUser) {
          navigate("/", { replace: true });
          return;
        }
        // 소셜은 가입과 로그인이 같은 엔드포인트라, 처음 가입한 사람만 약관과
        // 닉네임을 받는 화면을 한 번 지난다.
        navigate("/welcome", { replace: true });
      })
      .catch((error: unknown) => {
        if (error instanceof CallbackError) {
          setFailure(error.message);
          return;
        }
        if (error instanceof ApiError) {
          setFailure(
            error.code === "EMAIL_DUPLICATED"
              ? "이미 가입된 이메일입니다. 기존 방법으로 로그인해 주세요."
              : error.message,
          );
          return;
        }
        setFailure(
          "네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
        );
      });
  }, [provider, params, signInWithOAuth, navigate, handoff]);

  // 자동 이동이 막히는 브라우저가 있다. 눌러서 넘어갈 길을 같이 둔다.
  if (handoff) {
    return (
      <>
        <TopBar title="소셜 로그인" />

        <div className="px-6 pt-2 pb-10">
          <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
            앱으로 돌아가는 중이에요
          </h1>
          <p className="mt-3 text-[14px] leading-relaxed text-gray-500">
            화면이 그대로면 아래 버튼을 눌러주세요.
          </p>

          <Button
            type="button"
            className="mt-10"
            onClick={() => window.location.replace(handoff)}
          >
            꼬밋 앱으로 돌아가기
          </Button>
        </div>
      </>
    );
  }

  if (!failure) return <LoadingScreen />;

  return (
    <>
      <TopBar title="소셜 로그인" />

      <div className="px-6 pt-2 pb-10">
        <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
          로그인하지 못했어요
        </h1>
        <p className="mt-3 text-[14px] leading-relaxed text-gray-500">
          {failure}
        </p>

        <Button
          type="button"
          className="mt-10"
          onClick={() => navigate("/login", { replace: true })}
        >
          로그인으로 돌아가기
        </Button>
      </div>
    </>
  );
}

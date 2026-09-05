import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import * as authApi from "../domains/auth/api";
import type { OAuthProviderId } from "../domains/auth/oauth";
import { getMyProfile } from "../domains/user/api";
import { setSessionExpiredHandler } from "../shared/api/client";
import { tokenStore } from "../shared/api/tokenStore";
import type { UserProfileResponse } from "../shared/api/types";
import { AuthContext, type AuthStatus } from "./auth-context";

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserProfileResponse | null>(null);
  // RT 가 없으면 복구할 것도 없다. 첫 렌더에서 바로 결론이 나므로 effect 로 미루지 않는다.
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokenStore.getRefreshToken() ? "loading" : "anonymous",
  );
  const navigate = useNavigate();

  // 새로고침하면 AT 는 메모리라 사라지고 RT 만 남는다. 그 RT 로 내 정보를 한 번 불러
  // 로그인 상태를 복구한다. client 가 AT 를 알아서 재발급한다.
  useEffect(() => {
    if (!tokenStore.getRefreshToken()) return;

    let cancelled = false;
    getMyProfile()
      .then((profile) => {
        if (cancelled) return;
        setUser(profile);
        setStatus("authenticated");
      })
      .catch(() => {
        if (cancelled) return;
        // RT 폐기가 확정(401)이면 client 가 이미 지웠다. 여기서 마저 지우면
        // 일시적 오프라인 · 서버 오류에도 로그인이 풀린다 — 비로그인으로만 내려놓는다.
        setStatus("anonymous");
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // 로그인 상태로 쓰던 중에 갱신이 최종 실패하면 화면 어디에 있든 로그인으로 보낸다.
  // 부팅 복구 실패는 조용히 비로그인으로 내려놓는다 — 탭 3개는 비로그인도 들어가는
  // 화면이라, 오래 안 온 방문자가 홈 대신 로그인 화면을 보면 안 된다.
  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser(null);
      setStatus("anonymous");
      if (status === "authenticated") navigate("/login", { replace: true });
    });
  }, [navigate, status]);

  const signIn = useCallback(async (email: string, password: string) => {
    const result = await authApi.login({ email, password });
    setUser(result.user);
    setStatus("authenticated");
  }, []);

  const signInWithOAuth = useCallback(
    async (provider: OAuthProviderId, body: authApi.OAuthLoginRequest) => {
      const result = await authApi.oauthLogin(provider, body);
      setUser(result.user);
      setStatus("authenticated");
      return result.newUser;
    },
    [],
  );

  const signOut = useCallback(async () => {
    await authApi.logout().catch(() => undefined);
    setUser(null);
    setStatus("anonymous");
  }, []);

  const replaceUser = useCallback(
    (next: UserProfileResponse) => setUser(next),
    [],
  );

  const value = useMemo(
    () => ({ status, user, signIn, signInWithOAuth, signOut, replaceUser }),
    [status, user, signIn, signInWithOAuth, signOut, replaceUser],
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}

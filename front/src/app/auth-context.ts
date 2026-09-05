import { createContext } from "react";
import type { OAuthLoginRequest } from "../domains/auth/api";
import type { OAuthProviderId } from "../domains/auth/oauth";
import type { UserProfileResponse } from "../shared/api/types";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthContextValue {
  status: AuthStatus;
  user: UserProfileResponse | null;
  signIn(email: string, password: string): Promise<void>;
  /** @returns 이번 요청으로 계정이 새로 만들어졌는지. 닉네임 설정 유도에 쓴다. */
  signInWithOAuth(
    provider: OAuthProviderId,
    body: OAuthLoginRequest,
  ): Promise<boolean>;
  signOut(): Promise<void>;
  /** 프로필 수정 후 화면에 즉시 반영할 때 쓴다. */
  replaceUser(user: UserProfileResponse): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

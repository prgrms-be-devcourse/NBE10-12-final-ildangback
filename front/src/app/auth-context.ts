import { createContext } from "react";
import type { UserProfileResponse } from "../shared/api/types";

export type AuthStatus = "loading" | "authenticated" | "anonymous";

export interface AuthContextValue {
  status: AuthStatus;
  user: UserProfileResponse | null;
  signIn(email: string, password: string): Promise<void>;
  signOut(): Promise<void>;
  /** 프로필 수정 후 화면에 즉시 반영할 때 쓴다. */
  replaceUser(user: UserProfileResponse): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

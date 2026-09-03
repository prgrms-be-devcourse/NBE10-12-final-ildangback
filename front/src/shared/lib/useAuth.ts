import { useContext } from "react";
import { AuthContext, type AuthContextValue } from "../../app/auth-context";

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth 는 AuthProvider 안에서만 쓸 수 있다.");
  return context;
}

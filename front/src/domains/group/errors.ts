import { ApiError, SessionExpiredError } from "../../shared/api/client";

export function groupErrorMessage(error: unknown): string | null {
  if (error instanceof SessionExpiredError) return null;
  return error instanceof ApiError
    ? error.message
    : "네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
}

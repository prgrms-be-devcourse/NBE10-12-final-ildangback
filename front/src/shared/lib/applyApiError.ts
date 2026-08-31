import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError, SessionExpiredError } from "../api/client";

export interface ApplyApiErrorOptions {
  /** 이 폼이 가진 필드 이름들. 서버가 준 field 가 여기 있어야 인풋 밑에 붙일 수 있다. */
  fields: readonly string[];
  /**
   * code 별로 폼 상단 문구를 갈아끼운다.
   * INVALID_CREDENTIALS 문구가 ErrorCode 에 하나뿐이라 비밀번호 변경 · 탈퇴 화면에서도
   * "이메일 또는 비밀번호가..." 로 오기 때문이다. api.yaml 이 이 두 곳만 지정해 뒀다.
   */
  messages?: Record<string, string>;
  /** code 별로 그 오류를 붙일 필드. 409 중복처럼 어느 칸이 문제인지 아는 경우에 쓴다. */
  toField?: Record<string, string>;
}

/**
 * 서버 오류를 폼에 반영한다.
 *
 * 기본 규칙은 하나다 — 서버 message 를 그대로 띄운다. 프론트에서 code 를 문구로 매핑하면
 * 백엔드가 문구를 고칠 때마다 어긋난다. 위 두 옵션이 그 규칙의 예외다.
 *
 * @returns 폼 상단에 띄울 문구. 필드별로 다 붙였으면 null.
 */
export function applyApiError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  { fields, messages = {}, toField = {} }: ApplyApiErrorOptions,
): string | null {
  if (error instanceof SessionExpiredError) {
    // AuthProvider 가 로그인 화면으로 보낸다. 여기서 또 띄우지 않는다.
    return null;
  }

  if (!(error instanceof ApiError)) {
    return "네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
  }

  const overridden = messages[error.code];
  if (overridden) return overridden;

  // INVALID_INPUT_VALUE 만 errors[] 에 필드별 사유가 담겨 온다.
  if (error.isValidationError) {
    const unmatched = error.errors.filter((e) => !fields.includes(e.field));
    for (const fieldError of error.errors) {
      if (fields.includes(fieldError.field)) {
        setError(fieldError.field as Path<T>, { message: fieldError.reason });
      }
    }
    // 폼에 없는 필드로 오면 사용자가 아무 표시도 못 본다. 그건 상단에 띄운다.
    return unmatched.length > 0
      ? unmatched.map((e) => e.reason).join(" ")
      : null;
  }

  const target = toField[error.code];
  if (target && fields.includes(target)) {
    setError(target as Path<T>, { message: error.message });
    return null;
  }

  return error.message;
}

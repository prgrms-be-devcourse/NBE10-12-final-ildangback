import { z } from "zod";

/**
 * 서버 제약(docs/api.yaml SignUpRequest)을 그대로 옮긴 것이다.
 * 여기 값을 바꾸기 전에 서버 애노테이션을 먼저 확인한다 — 어긋나면 통과시켜 놓고 400 을 받는다.
 */
export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 72;
export const NICKNAME_MIN = 2;
export const NICKNAME_MAX = 20;

/** 영문 1자 이상 + 숫자 1자 이상, 인쇄 가능 ASCII 만. 특수문자는 허용하되 강제하지 않는다. */
const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d)[\x21-\x7E]+$/;

/** 한글·영문·숫자, 단어 사이 공백 1칸까지. */
const NICKNAME_PATTERN =
  /^[가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+( [가-힣ㄱ-ㅎㅏ-ㅣa-zA-Z0-9]+)*$/;

export const emailField = z
  .email("이메일 형식이 아닙니다.")
  .max(255, "이메일은 255자 이하여야 합니다.");

export const passwordField = z
  .string()
  .min(PASSWORD_MIN, `비밀번호는 ${PASSWORD_MIN}자 이상이어야 합니다.`)
  .max(PASSWORD_MAX, `비밀번호는 ${PASSWORD_MAX}자 이하여야 합니다.`)
  .regex(
    PASSWORD_PATTERN,
    "영문과 숫자를 모두 포함해야 합니다. 공백과 한글은 쓸 수 없습니다.",
  );

export const nicknameField = z
  .string()
  .min(NICKNAME_MIN, `닉네임은 ${NICKNAME_MIN}자 이상이어야 합니다.`)
  .max(NICKNAME_MAX, `닉네임은 ${NICKNAME_MAX}자 이하여야 합니다.`)
  .regex(
    NICKNAME_PATTERN,
    "한글·영문·숫자만 쓸 수 있고 공백은 단어 사이 1칸까지입니다.",
  );

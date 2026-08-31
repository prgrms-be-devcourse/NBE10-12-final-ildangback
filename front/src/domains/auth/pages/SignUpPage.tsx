import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Link, useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { setFlash } from "../../../shared/lib/flash";
import {
  emailField,
  nicknameField,
  NICKNAME_MAX,
  passwordField,
  PASSWORD_MIN,
} from "../../../shared/lib/validation";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { PixelSparkle } from "../../../shared/ui/PixelSparkle";
import { TopBar } from "../../../shared/ui/TopBar";
import { checkEmail, checkNickname, signUp } from "../api";
import { TermsAgreement } from "../components/TermsAgreement";
import {
  EMPTY_TERMS,
  requiredTermsAccepted,
  type TermsState,
} from "../components/terms";

const schema = z
  .object({
    email: emailField,
    password: passwordField,
    passwordConfirm: z.string().min(1, "비밀번호를 다시 입력해주세요."),
    nickname: nicknameField,
  })
  .refine((v) => v.password === v.passwordConfirm, {
    path: ["passwordConfirm"],
    message: "비밀번호가 일치하지 않습니다.",
  });

type SignUpForm = z.infer<typeof schema>;

const FIELDS = ["email", "password", "nickname"] as const;

export function SignUpPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [terms, setTerms] = useState<TermsState>(EMPTY_TERMS);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    clearErrors,
    control,
    formState: { errors, isSubmitting },
  } = useForm<SignUpForm>({ resolver: zodResolver(schema), mode: "onTouched" });

  const nickname = useWatch({ control, name: "nickname" }) ?? "";

  // 중복 확인은 보장이 아니다(api.yaml). 가입 시점에 서버가 다시 검사하므로
  // 여기서는 409 를 미리 알려주는 용도로만 쓴다.
  const checkDuplicate = async (
    field: "email" | "nickname",
    value: string,
    format: z.ZodType<string>,
    check: (v: string) => Promise<{ available: boolean }>,
    takenMessage: string,
  ) => {
    // errors[field] 만으로는 첫 blur 를 막지 못한다. react-hook-form 이 커스텀 onBlur 를
    // zod 리졸버보다 먼저 부르기 때문에 이 시점의 errors 는 아직 비어 있다.
    // 그래서 형식 검사를 여기서 한 번 더 한다 — 규칙은 validation.ts 한 곳에서 가져온다.
    if (!value || errors[field] || !format.safeParse(value).success) return;
    try {
      const { available } = await check(value);
      if (!available) setError(field, { message: takenMessage });
      else clearErrors(field);
    } catch {
      // 중복 확인 실패는 가입을 막지 않는다. 서버가 최종 판정한다.
    }
  };

  const onSubmit = handleSubmit(async ({ email, password, nickname: nick }) => {
    setFormError(null);
    try {
      await signUp({ email, password, nickname: nick });
    } catch (error) {
      setFormError(
        applyApiError(error, setError, {
          fields: FIELDS,
          // 409 는 어느 칸이 겹쳤는지 알려주므로 그 인풋 밑에 붙인다.
          // ACCOUNT_INFO_DUPLICATED 는 동시 가입으로 DB 유니크에 걸린 경우라
          // 서버도 어느 컬럼인지 모른다 — 그건 폼 상단에 그대로 둔다.
          toField: {
            EMAIL_DUPLICATED: "email",
            NICKNAME_DUPLICATED: "nickname",
          },
        }),
      );
      return;
    }

    // 가입은 토큰을 주지 않는다(api.yaml). 방금 받은 값으로 곧바로 로그인한다.
    try {
      await signIn(email, password);
      navigate("/", { replace: true });
    } catch {
      // 여기 왔으면 계정은 이미 만들어져 있다. "가입 실패" 를 띄우면 사용자가
      // 다시 가입을 시도하고 409 를 맞는다.
      setFlash("가입이 완료됐어요. 로그인해 주세요.");
      navigate("/login", { replace: true, state: { email } });
    }
  });

  const canSubmit = requiredTermsAccepted(terms);

  return (
    <>
      <TopBar />

      <div className="flex flex-1 flex-col justify-between px-6 pt-1 pb-4">
        <div>
          {/* 시안 우상단의 픽셀 반짝임 장식 */}
          <div className="flex items-start justify-between">
            <h1 className="text-[24px] font-bold text-gray-900">
              꼬밋을 시작해볼까요?
            </h1>
            <PixelSparkle />
          </div>
          <p className="mt-1 text-[14px] text-gray-500">
            계정을 만들고 매일의 인증을 쌓아보세요
          </p>

          <form
            onSubmit={onSubmit}
            noValidate
            className="mt-4 flex flex-col gap-3"
          >
            <TextField
              label="이메일"
              type="email"
              autoComplete="email"
              placeholder="이메일을 입력해주세요"
              error={errors.email?.message}
              {...register("email", {
                onBlur: (e) =>
                  checkDuplicate(
                    "email",
                    e.target.value,
                    emailField,
                    checkEmail,
                    "이미 사용 중인 이메일입니다.",
                  ),
              })}
            />

            <TextField
              label="비밀번호"
              type="password"
              revealable
              autoComplete="new-password"
              placeholder={`영문 · 숫자 포함 ${PASSWORD_MIN}자 이상`}
              error={errors.password?.message}
              {...register("password")}
            />

            <TextField
              label="비밀번호 확인"
              type="password"
              revealable
              autoComplete="new-password"
              placeholder="비밀번호를 다시 입력해주세요"
              error={errors.passwordConfirm?.message}
              {...register("passwordConfirm")}
            />

            <TextField
              label="닉네임"
              maxLength={NICKNAME_MAX}
              placeholder={`2-${NICKNAME_MAX}자로 입력해주세요`}
              counter={`${nickname.length}/${NICKNAME_MAX}`}
              error={errors.nickname?.message}
              {...register("nickname", {
                onBlur: (e) =>
                  checkDuplicate(
                    "nickname",
                    e.target.value,
                    nicknameField,
                    checkNickname,
                    "이미 사용 중인 닉네임입니다.",
                  ),
              })}
            />

            <TermsAgreement value={terms} onChange={setTerms} />

            <FormAlert message={formError} />

            <Button
              type="submit"
              loading={isSubmitting}
              disabled={!canSubmit}
              className="mt-2"
            >
              회원가입
            </Button>
          </form>
        </div>

        <p className="mt-3 text-center text-[13px] text-gray-500">
          이미 계정이 있나요?{" "}
          <Link to="/login" className="font-semibold text-purple-500 underline">
            로그인
          </Link>
        </p>
      </div>
    </>
  );
}

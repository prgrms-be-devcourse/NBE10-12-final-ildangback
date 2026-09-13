import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router";
import { z } from "zod";
import { ApiError } from "../../../shared/api/client";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { setFlash } from "../../../shared/lib/flash";
import { useAuth } from "../../../shared/lib/useAuth";
import { passwordField, PASSWORD_MIN } from "../../../shared/lib/validation";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { LoadingScreen } from "../../../shared/ui/LoadingScreen";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import { checkPasswordResetToken, confirmPasswordReset } from "../api";

const schema = z
  .object({
    newPassword: passwordField,
    newPasswordConfirm: z.string().min(1, "비밀번호를 다시 입력해주세요."),
  })
  .refine((v) => v.newPassword === v.newPasswordConfirm, {
    path: ["newPasswordConfirm"],
    message: "비밀번호가 일치하지 않습니다.",
  });

type ResetForm = z.infer<typeof schema>;

type TokenState =
  { kind: "checking" } | { kind: "ready"; email: string } | { kind: "invalid" };

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const { signOut } = useAuth();
  const navigate = useNavigate();
  const [tokenState, setTokenState] = useState<TokenState>(() =>
    token ? { kind: "checking" } : { kind: "invalid" },
  );
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetForm>({ resolver: zodResolver(schema) });

  // 폼을 다 채운 뒤에야 "만료된 링크" 를 보게 되지 않도록 화면이 열릴 때 먼저 확인한다.
  // 이 호출은 토큰을 소비하지 않는다.
  useEffect(() => {
    if (!token) return;

    let cancelled = false;
    checkPasswordResetToken(token)
      .then(({ email }) => {
        if (!cancelled) setTokenState({ kind: "ready", email });
      })
      .catch(() => {
        if (!cancelled) setTokenState({ kind: "invalid" });
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const onSubmit = handleSubmit(async ({ newPassword }) => {
    setFormError(null);
    try {
      await confirmPasswordReset({ token, newPassword });
    } catch (error) {
      // 확인 이후 만료됐거나 다른 탭에서 이미 썼다. 폼을 계속 보여주면 같은 실패만 반복한다.
      if (error instanceof ApiError && error.code === "EMAIL_TOKEN_INVALID") {
        setTokenState({ kind: "invalid" });
        return;
      }
      setFormError(
        applyApiError(error, setError, {
          fields: ["newPassword"],
        }),
      );
      return;
    }

    // 서버가 모든 RT 를 폐기했다. 이 브라우저에 남은 토큰도 같이 지워야 죽은 RT 로
    // 갱신을 시도하지 않는다.
    await signOut().catch(() => undefined);
    setFlash("비밀번호를 바꿨어요. 새 비밀번호로 로그인해 주세요.");
    navigate("/login", { replace: true });
  });

  if (tokenState.kind === "checking") return <LoadingScreen />;

  return (
    <>
      <TopBar title="비밀번호 재설정" />

      <div className="px-6 pt-2 pb-10">
        {tokenState.kind === "invalid" ? (
          <>
            <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
              링크를 쓸 수 없어요
            </h1>
            <p className="mt-3 text-[14px] leading-relaxed text-gray-500">
              만료됐거나 이미 사용된 링크예요. 재설정 메일을 다시 요청해 주세요.
            </p>

            <Link to="/forgot-password" className="mt-10 block">
              <Button type="button">메일 다시 요청하기</Button>
            </Link>
          </>
        ) : (
          <>
            <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
              새 비밀번호를 정해주세요
            </h1>
            <p className="mt-2 text-[14px] text-gray-500">
              {tokenState.email} 계정의 비밀번호를 바꿉니다.
            </p>

            <form
              onSubmit={onSubmit}
              noValidate
              className="mt-8 flex flex-col gap-4"
            >
              <TextField
                label="새 비밀번호"
                type="password"
                revealable
                autoComplete="new-password"
                placeholder={`영문 · 숫자 포함 ${PASSWORD_MIN}자 이상`}
                error={errors.newPassword?.message}
                {...register("newPassword")}
              />

              <TextField
                label="새 비밀번호 확인"
                type="password"
                revealable
                autoComplete="new-password"
                placeholder="새 비밀번호를 다시 입력해주세요"
                error={errors.newPasswordConfirm?.message}
                {...register("newPasswordConfirm")}
              />

              <p className="text-[13px] leading-relaxed text-gray-500">
                비밀번호를 바꾸면 로그인된 모든 기기에서 로그아웃돼요.
              </p>

              <FormAlert message={formError} />

              <Button type="submit" loading={isSubmitting} className="mt-2">
                비밀번호 바꾸기
              </Button>
            </form>
          </>
        )}
      </div>
    </>
  );
}

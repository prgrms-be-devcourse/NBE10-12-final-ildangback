import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { tokenStore } from "../../../shared/api/tokenStore";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { setFlash } from "../../../shared/lib/flash";
import { PASSWORD_MIN, passwordField } from "../../../shared/lib/validation";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import { changePassword } from "../api";

const schema = z
  .object({
    currentPassword: z.string().min(1, "현재 비밀번호를 입력해주세요."),
    newPassword: passwordField,
    newPasswordConfirm: z.string().min(1, "새 비밀번호를 다시 입력해주세요."),
  })
  .refine((v) => v.newPassword === v.newPasswordConfirm, {
    path: ["newPasswordConfirm"],
    message: "비밀번호가 일치하지 않습니다.",
  })
  .refine((v) => v.currentPassword !== v.newPassword, {
    path: ["newPassword"],
    message: "현재 비밀번호와 다른 비밀번호를 입력해주세요.",
  });

type PasswordForm = z.infer<typeof schema>;

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordForm>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setFormError(null);
    try {
      await changePassword({ currentPassword, newPassword });

      // 서버가 본인의 모든 RT 를 폐기한다(api.yaml). 우리 RT 도 이미 죽었으므로
      // 들고 있어봐야 다음 갱신에서 401 이다. 지우고 다시 로그인하게 한다.
      tokenStore.clear();
      setFlash("비밀번호를 변경했어요. 다시 로그인해 주세요.");
      navigate("/login", { replace: true });
    } catch (error) {
      setFormError(
        applyApiError(error, setError, {
          fields: ["currentPassword", "newPassword"],
          messages: {
            INVALID_CREDENTIALS: "현재 비밀번호가 올바르지 않습니다.",
          },
        }),
      );
    }
  });

  return (
    <>
      <TopBar title="비밀번호 변경" />

      <div className="px-6 pb-10">
        <h1 className="text-[24px] font-bold text-gray-900">비밀번호 변경</h1>
        <p className="mt-2 text-[14px] text-gray-500">
          변경하면 모든 기기에서 로그아웃돼요.
        </p>

        <form
          onSubmit={onSubmit}
          noValidate
          className="mt-8 flex flex-col gap-4"
        >
          <TextField
            label="현재 비밀번호"
            type="password"
            revealable
            autoComplete="current-password"
            error={errors.currentPassword?.message}
            {...register("currentPassword")}
          />
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
            error={errors.newPasswordConfirm?.message}
            {...register("newPasswordConfirm")}
          />

          <FormAlert message={formError} />

          <Button type="submit" loading={isSubmitting} className="mt-2">
            변경하기
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            취소
          </Button>
        </form>
      </div>
    </>
  );
}

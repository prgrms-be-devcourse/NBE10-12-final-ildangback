import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { setFlash } from "../../../shared/lib/flash";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { Checkbox } from "../../../shared/ui/Checkbox";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import { deleteAccount } from "../api";

/**
 * 비밀번호를 필수로 두지 않는다. 소셜 전용 가입자(hasPassword=false)는 비밀번호가 없어서
 * 막으면 영영 탈퇴하지 못하고, 그쪽은 AT 가 이미 본인 증명이다.
 */
const schema = z.object({
  password: z.string(),
});

type DeleteForm = z.infer<typeof schema>;

export function DeleteAccountPage() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [acknowledged, setAcknowledged] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DeleteForm>({
    resolver: zodResolver(schema),
    defaultValues: { password: "" },
  });

  const onSubmit = handleSubmit(async ({ password }) => {
    setFormError(null);
    try {
      await deleteAccount(password || undefined);

      // 이 화면은 RequireAuth 안에 있어서, 로그인 상태가 비는 순간 RequireAuth 가
      // /login 으로 튕겨낸다. 그게 탈퇴 뒤 갈 곳으로도 맞다 — 같은 이메일로 재가입이
      // 되고 링크가 거기 있다. 문구만 flash 로 넘겨 리다이렉트와 경쟁하지 않게 한다.
      setFlash("탈퇴가 완료됐어요.");

      // 서버가 모든 RT 를 폐기하고 api 쪽에서 토큰을 지웠다.
      // AuthProvider 상태만 비우면 된다 — 로그아웃 호출은 이미 죽은 RT 라 의미가 없다.
      await signOut().catch(() => undefined);
    } catch (error) {
      setFormError(
        applyApiError(error, setError, {
          fields: ["password"],
          messages: { INVALID_CREDENTIALS: "비밀번호가 올바르지 않습니다." },
        }),
      );
    }
  });

  return (
    <>
      <TopBar title="회원 탈퇴" />

      <div className="px-6 pb-10">
        <h1 className="text-[24px] font-bold text-gray-900">회원 탈퇴</h1>
        <p className="mt-2 text-[14px] leading-relaxed text-gray-500">
          {user?.nickname} 님의 계정을 탈퇴 처리합니다.
        </p>

        <ul className="mt-6 rounded-2xl bg-purple-50 px-5 py-4 text-[13px] leading-relaxed text-gray-500">
          <li>· 인증 기록과 통계를 더 이상 볼 수 없어요.</li>
          <li>· 닉네임과 이메일은 알아볼 수 없는 값으로 바뀌어요.</li>
          <li>· 같은 이메일로 다시 가입할 수 있어요.</li>
        </ul>

        <form
          onSubmit={onSubmit}
          noValidate
          className="mt-8 flex flex-col gap-4"
        >
          {/* 비밀번호가 없는 계정은 물어볼 것이 없다. 서버도 확인을 건너뛴다. */}
          {user?.hasPassword && (
            <TextField
              label="비밀번호"
              type="password"
              revealable
              autoComplete="current-password"
              placeholder="본인 확인을 위해 입력해주세요"
              error={errors.password?.message}
              {...register("password")}
            />
          )}

          <Checkbox checked={acknowledged} onChange={setAcknowledged}>
            위 내용을 확인했으며 탈퇴에 동의합니다
          </Checkbox>

          <FormAlert message={formError} />

          <Button
            type="submit"
            variant="danger"
            loading={isSubmitting}
            disabled={!acknowledged}
            className="mt-2"
          >
            탈퇴하기
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            취소
          </Button>
        </form>
      </div>
    </>
  );
}

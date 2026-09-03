import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { takeFlash } from "../../../shared/lib/flash";
import { useAuth } from "../../../shared/lib/useAuth";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { Logo } from "../../../shared/ui/Logo";
import { TextField } from "../../../shared/ui/TextField";
import { useToast } from "../../../shared/lib/useToast";
import { TopBar } from "../../../shared/ui/TopBar";
import { SocialButtons } from "../components/SocialButtons";

const schema = z.object({
  email: z.email("이메일 형식이 아닙니다."),
  password: z.string().min(1, "비밀번호를 입력해주세요."),
});

type LoginForm = z.infer<typeof schema>;

export function LoginPage() {
  const { signIn } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [formError, setFormError] = useState<string | null>(null);

  const [notice] = useState(takeFlash);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      email: (location.state as { email?: string } | null)?.email ?? "",
    },
  });

  const onSubmit = handleSubmit(async ({ email, password }) => {
    setFormError(null);
    try {
      await signIn(email, password);
      const from = (location.state as { from?: { pathname: string } } | null)
        ?.from;
      navigate(from?.pathname ?? "/", { replace: true });
    } catch (error) {
      setFormError(
        applyApiError(error, setError, { fields: ["email", "password"] }),
      );
    }
  });

  return (
    <>
      <TopBar />

      <div className="px-6 pt-2 pb-10">
        <div className="flex justify-center py-2">
          <Logo className="h-14" />
        </div>

        <h1 className="mt-5 text-[26px] font-bold text-gray-900">
          다시 만나서 반가워요
        </h1>
        <p className="mt-2 text-[16px] text-gray-500">
          오늘의 꼬밋을 이어가볼까요?
        </p>

        {notice && (
          <p
            role="status"
            className="mt-5 rounded-xl bg-purple-50 px-4 py-3 text-[13px] text-purple-700"
          >
            {notice}
          </p>
        )}

        <form
          onSubmit={onSubmit}
          noValidate
          className="mt-8 flex flex-col gap-4"
        >
          <TextField
            label="이메일"
            type="email"
            autoComplete="email"
            placeholder="이메일을 입력해주세요"
            error={errors.email?.message}
            {...register("email")}
          />
          <TextField
            label="비밀번호"
            type="password"
            revealable
            autoComplete="current-password"
            placeholder="비밀번호를 입력해주세요"
            error={errors.password?.message}
            {...register("password")}
          />

          <div className="flex justify-end text-[13px]">
            <button
              type="button"
              className="font-medium text-purple-500 hover:underline"
              onClick={() => showToast("비밀번호 재설정 기능은 준비 중입니다.")}
            >
              비밀번호 찾기
            </button>
          </div>

          <FormAlert message={formError} />

          <Button type="submit" loading={isSubmitting} className="mt-2">
            로그인
          </Button>
        </form>

        <SocialButtons />

        <p className="mt-8 text-center text-[13px] text-gray-500">
          아직 계정이 없나요?{" "}
          <Link
            to="/signup"
            className="font-semibold text-purple-500 underline"
          >
            회원가입
          </Link>
        </p>
      </div>
    </>
  );
}

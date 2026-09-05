import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { emailField } from "../../../shared/lib/validation";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import { requestPasswordReset } from "../api";

const schema = z.object({ email: emailField });

type ForgotForm = z.infer<typeof schema>;

/** 서버의 최소 발송 간격과 같은 값이다. 그 안에 다시 부르면 조용히 아무것도 안 나간다. */
const RESEND_INTERVAL_SEC = 60;

export function ForgotPasswordPage() {
  // 보낸 주소를 들고 있어야 같은 주소로 다시 보낼 수 있다. null 이면 아직 폼 단계다.
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setTimeout(() => setCooldown((sec) => sec - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ForgotForm>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null);
    try {
      await requestPasswordReset(email);
      setSentTo(email);
      setCooldown(RESEND_INTERVAL_SEC);
    } catch (error) {
      setFormError(applyApiError(error, setError, { fields: ["email"] }));
    }
  });

  const resend = async () => {
    if (!sentTo) return;
    setResending(true);
    try {
      await requestPasswordReset(sentTo);
      setCooldown(RESEND_INTERVAL_SEC);
    } catch {
      // 여기는 항상 204 라 실패하면 네트워크 문제다. 화면 상태는 그대로 둔다.
      setFormError(
        "네트워크에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setResending(false);
    }
  };

  return (
    <>
      <TopBar title="비밀번호 찾기" />

      <div className="px-6 pt-2 pb-10">
        {sentTo ? (
          <>
            <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
              메일을 보냈어요
            </h1>
            {/*
              서버는 가입 여부도 인증 여부도 알려주지 않고, 미인증 계정에는 재설정 메일 대신
              인증 메일이 나간다. 어느 쪽인지 알 수 없으므로 "재설정 메일" 로 단정하지 않는다.
            */}
            {/* 주소 뒤에 조사를 붙이지 않는다. 받침 유무에 따라 "로" 와 "으로" 가 갈린다. */}
            <p className="mt-3 text-[14px] leading-relaxed text-gray-500">
              아래 주소로 메일을 보냈습니다. 메일함을 확인해 주세요.
            </p>
            <p className="mt-2 text-[15px] font-semibold break-all text-gray-900">
              {sentTo}
            </p>
            <p className="mt-3 text-[13px] leading-relaxed text-gray-500">
              메일의 링크는 시간이 지나면 만료돼요. 메일이 오지 않으면 스팸함도
              확인해 주세요.
            </p>

            <FormAlert message={formError} />

            <div className="mt-10 flex flex-col gap-3">
              <Link to="/login">
                <Button type="button">로그인으로 돌아가기</Button>
              </Link>

              <Button
                type="button"
                variant="secondary"
                loading={resending}
                disabled={cooldown > 0}
                onClick={resend}
              >
                {cooldown > 0
                  ? `${cooldown}초 후 다시 보낼 수 있어요`
                  : "메일 다시 보내기"}
              </Button>

              {/* 주소를 잘못 적었을 때. 입력값은 남아 있어 오타만 고치면 된다. */}
              <Button
                type="button"
                variant="ghost"
                onClick={() => setSentTo(null)}
              >
                주소 바꾸기
              </Button>
            </div>
          </>
        ) : (
          <>
            <h1 className="mt-4 text-[24px] font-bold text-balance text-gray-900">
              비밀번호를 잊으셨나요?
            </h1>
            <p className="mt-2 text-[14px] leading-relaxed text-gray-500">
              가입할 때 쓴 이메일로 재설정 링크를 보내드려요.
            </p>

            <form
              onSubmit={onSubmit}
              noValidate
              className="mt-8 flex flex-col gap-4"
            >
              <TextField
                label="이메일"
                type="email"
                autoComplete="email"
                autoCapitalize="none"
                spellCheck={false}
                placeholder="이메일을 입력해주세요"
                error={errors.email?.message}
                {...register("email")}
              />

              <FormAlert message={formError} />

              <Button type="submit" loading={isSubmitting} className="mt-2">
                재설정 메일 받기
              </Button>
            </form>
          </>
        )}
      </div>
    </>
  );
}

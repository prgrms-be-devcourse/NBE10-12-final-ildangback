import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { Navigate, useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { setFlash } from "../../../shared/lib/flash";
import { useAuth } from "../../../shared/lib/useAuth";
import { NICKNAME_MAX, nicknameField } from "../../../shared/lib/validation";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { PixelSparkle } from "../../../shared/ui/PixelSparkle";
import { TextField } from "../../../shared/ui/TextField";
import { updateMyProfile } from "../../user/api";
import { checkNickname } from "../api";
import { TermsAgreement } from "../components/TermsAgreement";
import {
  EMPTY_TERMS,
  requiredTermsAccepted,
  type TermsState,
} from "../components/terms";

const schema = z.object({ nickname: nicknameField });

type OnboardingForm = z.infer<typeof schema>;

/**
 * 소셜로 처음 가입한 사람만 한 번 지나는 화면.
 *
 * 소셜은 가입과 로그인이 같은 엔드포인트라 약관을 보여줄 자리가 로그인 흐름 안에 없다.
 * 서버가 준 newUser 로 여기에 보내서 닉네임과 약관을 한 번에 받는다.
 *
 * 닉네임은 서버가 지어준 `꼬밋러` + 숫자라 그대로 두면 전부 비슷한 이름이 된다.
 *
 * 동의는 이메일 가입과 마찬가지로 버튼을 여는 게이트로만 쓰인다 — users 에 컬럼이 없어
 * 저장되지 않는다. 증빙을 남기려면 컬럼이 생긴 뒤 여기서 같이 보내야 한다.
 */
export function SocialOnboardingPage() {
  const { user, replaceUser, signOut } = useAuth();
  const navigate = useNavigate();
  const [terms, setTerms] = useState<TermsState>(EMPTY_TERMS);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    control,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm<OnboardingForm>({
    resolver: zodResolver(schema),
    defaultValues: { nickname: user?.nickname ?? "" },
  });

  const nickname = useWatch({ control, name: "nickname" }) ?? "";

  // 주소창에 직접 쳐서 들어온 경우다. 이 화면은 소셜 콜백만 보낸다.
  if (!user) return <Navigate to="/login" replace />;

  const verifyNickname = async () => {
    if (
      nickname === user.nickname ||
      errors.nickname ||
      !nicknameField.safeParse(nickname).success
    )
      return;
    try {
      const { available } = await checkNickname(nickname);
      if (getValues("nickname") !== nickname) return;
      if (!available)
        setError("nickname", { message: "이미 사용 중인 닉네임입니다." });
    } catch {
      // 중복 확인 실패는 진행을 막지 않는다. 서버가 최종 판정한다.
    }
  };

  const leave = async () => {
    await signOut().catch(() => undefined);
    setFlash("약관에 동의하면 시작할 수 있어요.");
    navigate("/login", { replace: true });
  };

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      // 서버가 지어준 이름을 그대로 쓰겠다면 보낼 것이 없다.
      // 아무 필드도 없는 PATCH 는 400 이라 호출 자체를 건너뛴다.
      if (values.nickname !== user.nickname) {
        replaceUser(await updateMyProfile({ nickname: values.nickname }));
      }
      navigate("/", { replace: true });
    } catch (error) {
      setFormError(
        applyApiError(error, setError, {
          fields: ["nickname"],
          toField: { NICKNAME_DUPLICATED: "nickname" },
        }),
      );
    }
  });

  return (
    <div className="flex flex-1 flex-col justify-between px-6 pt-8 pb-4">
      <div>
        <div className="flex items-start justify-between">
          <h1 className="text-[24px] font-bold text-balance text-gray-900">
            거의 다 왔어요
          </h1>
          <PixelSparkle />
        </div>
        <p className="mt-1 text-[14px] text-gray-500">
          쓸 이름을 정하고 약관에 동의하면 시작해요
        </p>

        <form
          onSubmit={onSubmit}
          noValidate
          className="mt-8 flex flex-col gap-4"
        >
          <TextField
            label="닉네임"
            maxLength={NICKNAME_MAX}
            counter={`${nickname.length}/${NICKNAME_MAX}`}
            error={errors.nickname?.message}
            {...register("nickname", { onBlur: verifyNickname })}
          />
          <p className="-mt-2 text-[12px] text-gray-500">
            지금 이름은 자동으로 만들어진 값이에요. 언제든 설정에서 바꿀 수
            있어요.
          </p>

          <TermsAgreement value={terms} onChange={setTerms} />

          <FormAlert message={formError} />

          <Button
            type="submit"
            loading={isSubmitting}
            disabled={!requiredTermsAccepted(terms)}
            className="mt-2"
          >
            꼬밋 시작하기
          </Button>

          {/*
            동의하지 않으면 시작 버튼이 안 열린다. 출구가 없으면 이 화면에 갇히므로
            나가는 길을 둔다. 계정은 이미 만들어져 있어서 다시 로그인하면 여기로 돌아온다.
          */}
          <Button type="button" variant="ghost" onClick={leave}>
            나중에 하기
          </Button>
        </form>
      </div>
    </div>
  );
}

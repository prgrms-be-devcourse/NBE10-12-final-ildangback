import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { useNavigate } from "react-router";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { useAuth } from "../../../shared/lib/useAuth";
import { NICKNAME_MAX, nicknameField } from "../../../shared/lib/validation";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { useToast } from "../../../shared/lib/useToast";
import { TopBar } from "../../../shared/ui/TopBar";
import { checkNickname } from "../../auth/api";
import { updateMyProfile } from "../api";

const INTRODUCTION_MAX = 255;

const schema = z.object({
  nickname: nicknameField,
  introduction: z
    .string()
    .max(INTRODUCTION_MAX, `자기소개는 ${INTRODUCTION_MAX}자 이하여야 합니다.`),
});

type EditForm = z.infer<typeof schema>;

export function EditProfilePage() {
  const { user, replaceUser } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EditForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      nickname: user?.nickname ?? "",
      introduction: user?.introduction ?? "",
    },
  });

  const nickname = useWatch({ control, name: "nickname" }) ?? "";
  const introduction = useWatch({ control, name: "introduction" }) ?? "";

  const nicknameChanged = nickname !== user?.nickname;

  // check-nickname 은 가입 폼과 프로필 수정 두 곳에서 쓴다.
  // 보장이 아니라 409 를 미리 알려주는 용도다 — 저장 시점에 서버가 다시 검사한다.
  const verifyNickname = async () => {
    // errors.nickname 만으로는 첫 blur 를 막지 못한다 — react-hook-form 이 커스텀
    // onBlur 를 zod 리졸버보다 먼저 부른다. 그래서 형식 검사를 여기서 한 번 더 한다.
    if (
      !nicknameChanged ||
      errors.nickname ||
      !nicknameField.safeParse(nickname).success
    )
      return;
    try {
      const { available } = await checkNickname(nickname);
      if (!available)
        setError("nickname", { message: "이미 사용 중인 닉네임입니다." });
    } catch {
      // 중복 확인 실패는 저장을 막지 않는다. 서버가 최종 판정한다.
    }
  };
  const introductionChanged = introduction !== (user?.introduction ?? "");
  const changed = nicknameChanged || introductionChanged;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      // 바뀐 필드만 보낸다. 둘 다 없으면 서버가 400 이다.
      // introduction 은 빈 문자열이 "비우기"고 생략이 "그대로 두기"다 — null 을 보내면 안 된다.
      const updated = await updateMyProfile({
        ...(nicknameChanged ? { nickname: values.nickname } : {}),
        ...(introductionChanged ? { introduction: values.introduction } : {}),
      });
      replaceUser(updated);
      showToast("프로필이 수정되었습니다.");
      navigate("/profile/settings", { replace: true });
    } catch (error) {
      setFormError(
        applyApiError(error, setError, {
          fields: ["nickname", "introduction"],
          toField: { NICKNAME_DUPLICATED: "nickname" },
        }),
      );
    }
  });

  return (
    <>
      <TopBar title="프로필 정보 수정" />

      <div className="px-6 pb-10">
        <h1 className="text-[24px] font-bold text-gray-900">프로필 수정</h1>

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

          <TextField
            label="자기소개"
            maxLength={INTRODUCTION_MAX}
            placeholder="어떤 습관을 만들고 있나요?"
            error={errors.introduction?.message}
            {...register("introduction")}
          />
          <p className="-mt-2 text-[12px] text-gray-500">
            비우려면 내용을 지우고 저장하세요.
          </p>

          <FormAlert message={formError} />

          <Button
            type="submit"
            loading={isSubmitting}
            disabled={!changed}
            className="mt-2"
          >
            저장
          </Button>
          <Button type="button" variant="ghost" onClick={() => navigate(-1)}>
            취소
          </Button>
        </form>
      </div>
    </>
  );
}

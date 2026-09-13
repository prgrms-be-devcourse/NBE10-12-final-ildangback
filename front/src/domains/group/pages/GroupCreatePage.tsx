import { CaretLeftIcon } from "@phosphor-icons/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import type { FieldErrors } from "react-hook-form";
import { useNavigate } from "react-router";
import { ApiError } from "../../../shared/api/client";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { createGroup } from "../api";
import { GroupCreateSteps } from "../components/GroupCreateSteps";
import { CREATE_FIELDS, groupCreateSchema, STEP_FIELDS } from "../validation";
import type { GroupCreateForm } from "../validation";

const TITLES = [
  "어떤 챌린지를 시작할까요?",
  "챌린지 정보를 입력해주세요",
  "얼마나 자주 인증할까요?",
  "어떤 방식으로 인증할까요?",
  "그룹 운영 방식을 정해주세요",
  "그룹을 만들 준비가 됐어요",
];
const DESCRIPTIONS = [
  "그룹에서 함께 달성할 주제를 골라주세요.",
  "함께할 목표와 진행 기간을 정해주세요.",
  "챌린지에 맞는 반복 주기를 설정해주세요.",
  "사진으로 일상의 실천을 기록해요.",
  "공개 여부와 함께할 인원을 선택해주세요.",
  "설정을 확인하고 챌린지를 시작하세요.",
];
const SERVER_FIELDS = {
  START_DATE_INVALID: "challenge.startDate",
  INVALID_PERIOD: "challenge.endDate",
  INVALID_FREQUENCY: "challenge.frequencyType",
  INVALID_DAILY_COUNT: "challenge.dailyCheckInCount",
  NO_CHECK_IN_METHOD: "challenge.allowedTypes",
  INVALID_CATEGORY_MAP_TYPE: "category",
};

export function GroupCreatePage() {
  const [step, setStep] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const lock = useRef(false);
  const heading = useRef<HTMLHeadingElement>(null);
  const navigate = useNavigate();
  const { showToast } = useToast();
  const form = useForm<GroupCreateForm>({
    resolver: zodResolver(groupCreateSchema),
    mode: "onChange",
    shouldUnregister: false,
    defaultValues: {
      name: "",
      description: "",
      category: "EXERCISE",
      mapType: "GYM",
      visibility: "PUBLIC",
      maxMembers: 6,
      challenge: {
        startDate: "",
        endDate: "",
        frequencyType: "DAILY",
        frequencyValue: 2,
        daysOfWeek: [],
        dailyCheckInCount: 1,
        allowedTypes: ["PHOTO"],
      },
    },
  });
  const move = (next: number) => {
    setStep(next);
    window.scrollTo({ top: 0, behavior: "instant" });
    heading.current?.focus();
  };
  const invalid = (errors: FieldErrors<GroupCreateForm>) => {
    const first = STEP_FIELDS.findIndex((fields) =>
      fields.some((field) =>
        field
          .split(".")
          .reduce<unknown>(
            (value, key) =>
              value && typeof value === "object"
                ? (value as Record<string, unknown>)[key]
                : undefined,
            errors,
          ),
      ),
    );
    if (first >= 0) move(first);
    setFormError("입력 내용을 확인해주세요.");
  };
  const submit = async (values: GroupCreateForm) => {
    if (lock.current) return;
    lock.current = true;
    setPending(true);
    setFormError(null);
    try {
      const result = await createGroup({
        ...values,
        challenge: {
          ...values.challenge,
          frequencyValue:
            values.challenge.frequencyType === "EVERY_N_DAYS"
              ? values.challenge.frequencyValue
              : null,
          daysOfWeek:
            values.challenge.frequencyType === "DAYS_OF_WEEK"
              ? values.challenge.daysOfWeek
              : [],
          allowedTypes: ["PHOTO"],
        },
      });
      showToast("그룹을 만들었어요.");
      navigate(
        result.currentChallenge
          ? `/challenges/${result.currentChallenge.id}`
          : "/challenges",
        { replace: true },
      );
    } catch (error) {
      const message = applyApiError(error, form.setError, {
        fields: CREATE_FIELDS,
        toField: SERVER_FIELDS,
      });
      setFormError(message);
      if (error instanceof ApiError) {
        const fields = [
          ...error.errors.map((item) => item.field),
          SERVER_FIELDS[error.code as keyof typeof SERVER_FIELDS],
        ];
        const target = STEP_FIELDS.findIndex((stepFields) =>
          stepFields.some((field) => fields.includes(field)),
        );
        if (target >= 0) move(target);
      }
    } finally {
      lock.current = false;
      setPending(false);
    }
  };
  const next = async () => {
    if (pending) return;
    setFormError(null);
    if (await form.trigger([...STEP_FIELDS[step]], { shouldFocus: true }))
      move(step + 1);
  };
  return (
    <>
      <header className="relative flex h-16 items-center px-4">
        <button
          type="button"
          disabled={pending}
          onClick={() => (step > 0 ? move(step - 1) : navigate("/challenges"))}
          aria-label={step > 0 ? "이전 단계" : "그룹 목록으로"}
          className="p-2 disabled:opacity-40"
        >
          <CaretLeftIcon size={24} />
        </button>
        <span className="pointer-events-none absolute inset-x-0 text-center text-base font-bold">
          그룹 만들기
        </span>
      </header>
      <main className="px-5 pb-10">
        <ol
          aria-label="그룹 생성 단계"
          className="my-5 flex items-center justify-between"
        >
          {TITLES.map((title, index) => (
            <li
              key={title}
              aria-current={step === index ? "step" : undefined}
              className="flex flex-1 items-center last:flex-none"
            >
              <span
                aria-label={`${index + 1}단계 ${title}`}
                className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full border text-sm ${step === index ? "border-purple-500 bg-purple-500 text-white" : "border-purple-200 bg-purple-50 text-gray-500"}`}
              >
                {index + 1}
              </span>
              {index < 5 && <span className="h-px flex-1 bg-purple-200" />}
            </li>
          ))}
        </ol>
        <h1
          ref={heading}
          tabIndex={-1}
          className="mt-10 text-[23px] font-bold tracking-tight outline-none"
        >
          {TITLES[step]}
        </h1>
        <p className="mt-2 mb-7 text-sm text-gray-500">{DESCRIPTIONS[step]}</p>
        <FormProvider {...form}>
          <form
            className="flex min-h-[calc(100dvh-21rem)] flex-col"
            noValidate
            onSubmit={(event) => {
              event.preventDefault();
              if (step === 5) void form.handleSubmit(submit, invalid)(event);
              else void next();
            }}
          >
            <fieldset disabled={pending} className="min-w-0">
              <GroupCreateSteps step={step} />
            </fieldset>
            <div className="mt-6">
              <FormAlert message={formError} />
            </div>
            <div className="mt-auto flex gap-3 pt-8">
              {step === 5 && (
                <Button
                  type="button"
                  variant="secondary"
                  disabled={pending}
                  onClick={() => move(0)}
                >
                  수정하기
                </Button>
              )}
              <Button type="submit" loading={pending}>
                {step === 5 ? "그룹 만들기" : "다음"}
              </Button>
            </div>
          </form>
        </FormProvider>
      </main>
    </>
  );
}

import { zodResolver } from "@hookform/resolvers/zod";
import { useRef, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { applyApiError } from "../../../shared/lib/applyApiError";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { groupCreateSchema, WEEKDAYS } from "../../group/validation";
import { updateChallenge } from "../api";
import { isSupportedCheckInType, toggleAllowedType } from "../types";
import type { ChallengeDetail } from "../types";

const schema = groupCreateSchema.shape.challenge.superRefine((value, ctx) => {
  if (value.endDate < value.startDate)
    ctx.addIssue({
      code: "custom",
      path: ["endDate"],
      message: "종료일은 시작일보다 빠를 수 없어요.",
    });
  if (value.frequencyType === "DAYS_OF_WEEK" && value.daysOfWeek.length === 0)
    ctx.addIssue({
      code: "custom",
      path: ["daysOfWeek"],
      message: "인증할 요일을 선택해주세요.",
    });
  if (value.frequencyType === "EVERY_N_DAYS" && value.frequencyValue === null)
    ctx.addIssue({
      code: "custom",
      path: ["frequencyValue"],
      message: "2~7일 간격을 선택해주세요.",
    });
});
type SettingsForm = z.infer<typeof schema>;

/**
 * 챌린지 도메인의 `CheckInType` 은 LIVE 도 포함(별도 서브시스템, 아직 없음) — 이 폼은
 * PHOTO/VIDEO 만 다룬다(사진/영상 동시 허용 가능). 기존 값에 LIVE 만 있는 이상 상태는
 * 없으니 필터 결과가 비면 PHOTO 로 되돌린다.
 */
function toSupportedAllowedTypesOrDefault(
  allowedTypes: ChallengeDetail["allowedTypes"],
): SettingsForm["allowedTypes"] {
  const supported = allowedTypes.filter(isSupportedCheckInType);
  return supported.length ? supported : ["PHOTO"];
}

const FIELDS = [
  "startDate",
  "endDate",
  "frequencyType",
  "frequencyValue",
  "daysOfWeek",
  "dailyCheckInCount",
  "allowedTypes",
];
export function ChallengeSettingsEditor({
  challenge,
  onSaved,
  onClose,
}: {
  challenge: ChallengeDetail;
  onSaved: () => void;
  onClose: () => void;
}) {
  const { showToast } = useToast();
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);
  const form = useForm<SettingsForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      ...challenge,
      daysOfWeek: challenge.daysOfWeek ?? [],
      frequencyValue: challenge.frequencyValue ?? 2,
      allowedTypes: toSupportedAllowedTypesOrDefault(challenge.allowedTypes),
    },
  });
  const frequencyType = useWatch({
    control: form.control,
    name: "frequencyType",
  });
  const allowedTypes = useWatch({
    control: form.control,
    name: "allowedTypes",
  });
  const days = useWatch({ control: form.control, name: "daysOfWeek" });
  const { errors, isSubmitting } = form.formState;
  const submit = async (values: SettingsForm) => {
    if (busy.current) return;
    busy.current = true;
    setError(null);
    try {
      const { startDate, ...settings } = values;
      await updateChallenge(challenge.id, {
        ...settings,
        ...(challenge.seqNo === 1 ? { startDate } : {}),
        frequencyValue:
          frequencyType === "EVERY_N_DAYS" ? values.frequencyValue : null,
        daysOfWeek: frequencyType === "DAYS_OF_WEEK" ? values.daysOfWeek : [],
      });
      showToast("챌린지 설정을 저장했어요.");
      onSaved();
      onClose();
    } catch (err) {
      setError(
        applyApiError(err, form.setError, {
          fields: FIELDS,
          toField: {
            START_DATE_INVALID: "startDate",
            INVALID_PERIOD: "endDate",
            INVALID_FREQUENCY: "frequencyType",
            INVALID_DAILY_COUNT: "dailyCheckInCount",
            NO_CHECK_IN_METHOD: "allowedTypes",
            EXTENSION_START_DATE_NOT_EDITABLE: "startDate",
          },
        }),
      );
    } finally {
      busy.current = false;
    }
  };
  return (
    <form
      noValidate
      onSubmit={(event) => void form.handleSubmit(submit)(event)}
      className="space-y-4 rounded-2xl border border-purple-200 p-4"
    >
      <h2 className="font-bold">시작 전 설정 수정</h2>
      <fieldset disabled={isSubmitting} className="min-w-0 space-y-4">
        <TextField
          label="시작일"
          type="date"
          readOnly={challenge.seqNo > 1}
          error={errors.startDate?.message}
          {...form.register("startDate")}
        />
        {challenge.seqNo > 1 && (
          <p className="text-xs text-gray-500">
            연장 시즌의 시작일은 변경할 수 없어요.
          </p>
        )}
        <TextField
          label="종료일"
          type="date"
          error={errors.endDate?.message}
          {...form.register("endDate")}
        />
        <label className="block text-sm font-semibold">
          인증 빈도
          <select
            {...form.register("frequencyType")}
            className="mt-2 w-full rounded-xl border border-purple-200 bg-white p-3"
          >
            <option value="DAILY">매일</option>
            <option value="EVERY_N_DAYS">N일마다</option>
            <option value="DAYS_OF_WEEK">요일 지정</option>
          </select>
        </label>
        <FormAlert message={errors.frequencyType?.message ?? null} />
        {frequencyType === "EVERY_N_DAYS" && (
          <TextField
            label="인증 간격 (2~7일)"
            type="number"
            min={2}
            max={7}
            error={errors.frequencyValue?.message}
            {...form.register("frequencyValue", { valueAsNumber: true })}
          />
        )}
        {frequencyType === "DAYS_OF_WEEK" && (
          <>
            <div className="grid grid-cols-7 gap-1">
              {WEEKDAYS.map((day, index) => (
                <button
                  type="button"
                  key={day}
                  aria-pressed={days.includes(day)}
                  onClick={() =>
                    form.setValue(
                      "daysOfWeek",
                      days.includes(day)
                        ? days.filter((value) => value !== day)
                        : [...days, day],
                      { shouldValidate: true },
                    )
                  }
                  className={`min-h-11 rounded-lg border text-sm ${days.includes(day) ? "border-purple-500 bg-purple-50 text-purple-700" : "border-purple-200"}`}
                >
                  {["월", "화", "수", "목", "금", "토", "일"][index]}
                </button>
              ))}
            </div>
            <FormAlert message={errors.daysOfWeek?.message ?? null} />
          </>
        )}
        <TextField
          label="하루 인증 횟수 (1~4회)"
          type="number"
          min={1}
          max={4}
          error={errors.dailyCheckInCount?.message}
          {...form.register("dailyCheckInCount", { valueAsNumber: true })}
        />
        <label className="block text-sm font-semibold">
          인증 방식
          <div className="mt-2 grid grid-cols-2 gap-2">
            {(["PHOTO", "VIDEO"] as const).map((type) => (
              <button
                key={type}
                type="button"
                aria-pressed={allowedTypes.includes(type)}
                onClick={() =>
                  form.setValue(
                    "allowedTypes",
                    toggleAllowedType(allowedTypes, type),
                    { shouldValidate: true },
                  )
                }
                className={`min-h-11 rounded-xl border text-sm font-medium ${
                  allowedTypes.includes(type)
                    ? "border-purple-500 bg-purple-50 text-purple-700"
                    : "border-purple-200 text-gray-500"
                }`}
              >
                {type === "PHOTO" ? "사진" : "영상"}
              </button>
            ))}
          </div>
        </label>
        <FormAlert message={errors.allowedTypes?.message ?? null} />
      </fieldset>
      <FormAlert message={error} />
      <div className="flex gap-2">
        <Button
          type="button"
          variant="secondary"
          disabled={isSubmitting}
          onClick={onClose}
        >
          취소
        </Button>
        <Button type="submit" loading={isSubmitting}>
          설정 저장
        </Button>
      </div>
    </form>
  );
}

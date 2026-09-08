import {
  BarbellIcon,
  BookOpenIcon,
  CameraIcon,
  CheckCircleIcon,
} from "@phosphor-icons/react";
import { useFormContext, useWatch } from "react-hook-form";
import { TextField } from "../../../shared/ui/TextField";
import {
  ChallengeRulesCard,
  InfoRow,
} from "../../challenge/components/ChallengeInfo";
import { frequencyLabel } from "../../challenge/presentation";
import type { FrequencyType } from "../../challenge/types";
import { CATEGORIES, CATEGORY_LABEL, categoryMap } from "../constants";
import {
  endDateForDuration,
  GROUP_DESCRIPTION_MAX,
  GROUP_NAME_MAX,
  WEEKDAYS,
} from "../validation";
import type { GroupCreateForm } from "../validation";

const SELECTED = "border-purple-500 bg-purple-50 text-purple-700";
const NORMAL = "border-purple-200 bg-white text-gray-500";
export function GroupCreateSteps({ step }: { step: number }) {
  const {
    register,
    control,
    setValue,
    getValues,
    clearErrors,
    formState: { errors },
  } = useFormContext<GroupCreateForm>();
  const values = useWatch({ control }) as GroupCreateForm;
  const settings = values.challenge;
  const pickFrequency = (value: FrequencyType) => {
    clearErrors([
      "challenge.frequencyType",
      "challenge.frequencyValue",
      "challenge.daysOfWeek",
    ]);
    setValue("challenge.frequencyType", value);
    // Keep hidden choices while navigating, normalize irrelevant fields on submission.
    if (
      value === "EVERY_N_DAYS" &&
      getValues("challenge.frequencyValue") === null
    )
      setValue("challenge.frequencyValue", 2);
  };
  if (step === 0)
    return (
      <div className="space-y-4">
        {(["STUDY_ROOM", "GYM"] as const).map((map) => (
          <section
            key={map}
            className="rounded-2xl border border-purple-200 p-4"
          >
            <h2 className="mb-4 flex items-center gap-2 text-lg font-bold">
              {map === "GYM" ? (
                <BarbellIcon size={25} />
              ) : (
                <BookOpenIcon size={25} />
              )}
              {map === "GYM" ? "운동 맵" : "스터디룸 맵"}
            </h2>
            <div
              className={`grid gap-2 ${map === "GYM" ? "grid-cols-2" : "grid-cols-3"}`}
            >
              {CATEGORIES.filter(
                (category) => categoryMap(category) === map,
              ).map((category) => (
                <button
                  type="button"
                  key={category}
                  aria-pressed={values.category === category}
                  onClick={() => {
                    setValue("category", category);
                    setValue("mapType", map);
                  }}
                  className={`min-h-14 rounded-xl border text-sm font-semibold ${values.category === category ? SELECTED : NORMAL}`}
                >
                  {CATEGORY_LABEL[category]}
                </button>
              ))}
            </div>
          </section>
        ))}
        <p className="pt-4 text-sm text-purple-500">
          함께 {CATEGORY_LABEL[values.category]} 습관을 만들어가요.
        </p>
        <FieldError
          message={errors.category?.message ?? errors.mapType?.message}
        />
      </div>
    );
  if (step === 1)
    return (
      <div className="space-y-5">
        <TextField
          label="그룹명"
          maxLength={GROUP_NAME_MAX}
          counter={`${values.name.length}/${GROUP_NAME_MAX}`}
          error={errors.name?.message}
          {...register("name")}
        />
        <div>
          <label
            htmlFor="group-description"
            className="mb-2 block text-[13px] font-semibold"
          >
            그룹 소개
          </label>
          <textarea
            id="group-description"
            rows={3}
            maxLength={GROUP_DESCRIPTION_MAX}
            placeholder="함께할 챌린지를 소개해주세요"
            aria-invalid={!!errors.description}
            aria-describedby={
              errors.description ? "description-error" : undefined
            }
            {...register("description")}
            className="w-full resize-y rounded-xl border border-purple-200 px-4 py-3 focus:border-purple-500 focus:ring-2 focus:ring-purple-300 focus:outline-none"
          />
          <p className="mt-1 text-right text-xs text-gray-500">
            {values.description.length}/{GROUP_DESCRIPTION_MAX}
          </p>
          <FieldError
            id="description-error"
            message={errors.description?.message}
          />
        </div>
        <div className="border-t border-purple-200 pt-5">
          <TextField
            label="시작일"
            type="date"
            error={errors.challenge?.startDate?.message}
            {...register("challenge.startDate")}
          />
          <p className="mt-2 text-xs text-gray-500">
            시작일은 내일 이후로 선택해주세요.
          </p>
        </div>
        <fieldset>
          <legend className="mb-3 text-sm font-semibold">
            진행 기간 빠른 선택
          </legend>
          <div className="grid grid-cols-4 gap-2">
            {[30, 90, 180, 365].map((days) => (
              <button
                key={days}
                type="button"
                disabled={!settings.startDate}
                onClick={() => {
                  const end = endDateForDuration(settings.startDate, days);
                  if (end)
                    setValue("challenge.endDate", end, {
                      shouldValidate: true,
                    });
                }}
                className="min-h-11 rounded-xl border border-purple-200 text-sm text-purple-700 disabled:opacity-40"
              >
                {days === 365 ? "365일" : `${days}일`}
              </button>
            ))}
          </div>
        </fieldset>
        <TextField
          label="종료일 (직접 선택 가능)"
          type="date"
          min={settings.startDate || undefined}
          error={errors.challenge?.endDate?.message}
          {...register("challenge.endDate")}
        />
        <p className="rounded-xl bg-purple-50 p-3 text-xs leading-relaxed text-purple-700">
          기간은 시작일과 종료일을 모두 포함해요. 챌린지가 시작된 후에는 규칙을
          변경할 수 없어요.
        </p>
      </div>
    );
  if (step === 2)
    return (
      <div className="space-y-4">
        {(
          [
            {
              value: "DAILY",
              title: "매일",
              description: "매일 정해진 횟수만큼 인증",
            },
            {
              value: "EVERY_N_DAYS",
              title: "N일마다",
              description: "최소 2일 ~ 최대 7일 간격",
            },
            {
              value: "DAYS_OF_WEEK",
              title: "요일 지정",
              description: "선택한 요일마다 인증",
            },
          ] as const
        ).map((option) => (
          <section
            key={option.value}
            className={`rounded-2xl border p-4 ${settings.frequencyType === option.value ? "border-purple-500" : "border-purple-200"}`}
          >
            <label className="flex cursor-pointer items-center gap-3">
              <input
                type="radio"
                name="frequency"
                checked={settings.frequencyType === option.value}
                onChange={() => pickFrequency(option.value)}
                className="h-5 w-5 accent-purple-500"
              />
              <span>
                <span className="block font-bold">{option.title}</span>
                <span className="text-xs text-gray-500">
                  {option.description}
                </span>
              </span>
            </label>
            {option.value === "EVERY_N_DAYS" &&
              settings.frequencyType === option.value && (
                <div className="mt-4">
                  <Counter
                    label="인증 간격"
                    value={settings.frequencyValue ?? 2}
                    min={2}
                    max={7}
                    unit="일"
                    onChange={(value) =>
                      setValue("challenge.frequencyValue", value, {
                        shouldValidate: true,
                      })
                    }
                  />
                </div>
              )}
            {option.value === "DAYS_OF_WEEK" &&
              settings.frequencyType === option.value && (
                <div className="mt-4 grid grid-cols-7 gap-1">
                  {WEEKDAYS.map((day, index) => (
                    <button
                      type="button"
                      key={day}
                      aria-label={`${["월", "화", "수", "목", "금", "토", "일"][index]}요일`}
                      aria-pressed={settings.daysOfWeek.includes(day)}
                      onClick={() =>
                        setValue(
                          "challenge.daysOfWeek",
                          settings.daysOfWeek.includes(day)
                            ? settings.daysOfWeek.filter(
                                (value) => value !== day,
                              )
                            : [...settings.daysOfWeek, day],
                          { shouldValidate: true },
                        )
                      }
                      className={`min-h-11 rounded-lg border text-xs ${settings.daysOfWeek.includes(day) ? SELECTED : NORMAL}`}
                    >
                      {["월", "화", "수", "목", "금", "토", "일"][index]}
                    </button>
                  ))}
                </div>
              )}
          </section>
        ))}
        <FieldError
          message={
            errors.challenge?.daysOfWeek?.message ??
            errors.challenge?.frequencyValue?.message ??
            errors.challenge?.frequencyType?.message
          }
        />
        <div className="border-t border-purple-200 pt-5">
          <h2 className="mb-2 font-semibold">하루 인증 횟수</h2>
          <p className="mb-4 text-xs text-gray-500">
            인증하기로 한 날, 하루에 몇 번 인증할지 정해주세요.
          </p>
          <Counter
            label="하루 인증 횟수"
            value={settings.dailyCheckInCount}
            min={1}
            max={10}
            unit="회"
            onChange={(value) =>
              setValue("challenge.dailyCheckInCount", value, {
                shouldValidate: true,
              })
            }
          />
          <FieldError message={errors.challenge?.dailyCheckInCount?.message} />
        </div>
        <p className="rounded-xl bg-purple-50 p-3 text-sm text-purple-700">
          {frequencyLabel(settings)} · 하루 {settings.dailyCheckInCount}회 인증
        </p>
      </div>
    );
  if (step === 3)
    return (
      <div className="rounded-2xl border border-purple-500 bg-purple-50 p-5">
        <div className="flex items-center gap-4">
          <CameraIcon size={36} className="text-purple-500" />
          <div className="flex-1">
            <h2 className="text-lg font-bold">사진</h2>
            <p className="mt-1 text-sm text-gray-500">
              사진으로 인증하는 챌린지예요.
            </p>
          </div>
          <CheckCircleIcon
            size={26}
            weight="fill"
            className="text-purple-500"
            aria-label="선택됨"
          />
        </div>
        <p className="mt-5 text-xs text-purple-700">
          현재는 사진 인증 방식으로만 그룹을 만들 수 있어요.
        </p>
        <FieldError message={errors.challenge?.allowedTypes?.message} />
      </div>
    );
  if (step === 4)
    return (
      <div className="space-y-4">
        {(
          [
            {
              value: "PUBLIC",
              label: "공개방",
              description: "그룹 탐색에서 누구나 참여",
            },
            {
              value: "CODE_ONLY",
              label: "비밀방",
              description: "공개 목록에 표시되지 않는 그룹",
            },
          ] as const
        ).map((option) => (
          <label
            key={option.value}
            className={`flex cursor-pointer items-center gap-3 rounded-2xl border p-5 ${values.visibility === option.value ? "border-purple-500" : "border-purple-200"}`}
          >
            <input
              type="radio"
              value={option.value}
              {...register("visibility")}
              className="h-5 w-5 accent-purple-500"
            />
            <span>
              <span className="block font-bold">{option.label}</span>
              <span className="text-xs text-gray-500">
                {option.description}
              </span>
            </span>
          </label>
        ))}
        {values.visibility === "CODE_ONLY" && (
          <p className="rounded-xl bg-purple-50 p-3 text-xs leading-relaxed text-purple-700">
            생성 후 그룹 관리에서 초대코드를 확인해 친구에게 알려주세요.
          </p>
        )}
        <fieldset className="pt-5">
          <legend className="mb-4 font-semibold">최대 인원 (나 포함)</legend>
          <div className="grid grid-cols-6 gap-2">
            {[1, 2, 3, 4, 5, 6].map((count) => (
              <button
                key={count}
                type="button"
                aria-label={`최대 ${count}명`}
                aria-pressed={values.maxMembers === count}
                onClick={() => setValue("maxMembers", count)}
                className={`aspect-square rounded-full text-lg font-bold ${values.maxMembers === count ? "bg-purple-500 text-white" : "bg-gray-100 text-gray-500"}`}
              >
                {count}
              </button>
            ))}
          </div>
        </fieldset>
      </div>
    );
  return (
    <div className="space-y-4">
      <section className="rounded-2xl border border-purple-200 p-4">
        <h2 className="font-bold">그룹 설정</h2>
        <dl className="mt-2 divide-y divide-purple-100">
          <InfoRow label="그룹명">{values.name}</InfoRow>
          <InfoRow label="그룹 소개">
            {values.description || "입력하지 않음"}
          </InfoRow>
          <InfoRow label="카테고리">{CATEGORY_LABEL[values.category]}</InfoRow>
          <InfoRow label="맵">
            {values.mapType === "GYM" ? "운동 맵" : "스터디룸 맵"}
          </InfoRow>
          <InfoRow label="공개 여부">
            {values.visibility === "PUBLIC" ? "공개" : "비공개"}
          </InfoRow>
          <InfoRow label="최대 인원">{values.maxMembers}명</InfoRow>
        </dl>
      </section>
      <ChallengeRulesCard settings={settings} />
    </div>
  );
}
function Counter({
  label,
  value,
  min,
  max,
  unit,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  unit: string;
  onChange: (value: number) => void;
}) {
  return (
    <div className="flex items-center justify-between rounded-xl border border-purple-200 px-2">
      <button
        type="button"
        aria-label={`${label} 줄이기`}
        disabled={value <= min}
        onClick={() => onChange(value - 1)}
        className="h-12 w-12 text-xl text-gray-500 disabled:opacity-30"
      >
        −
      </button>
      <output aria-label={label} className="font-bold text-purple-500">
        {value}
        {unit}
      </output>
      <button
        type="button"
        aria-label={`${label} 늘리기`}
        disabled={value >= max}
        onClick={() => onChange(value + 1)}
        className="h-12 w-12 text-xl text-gray-500 disabled:opacity-30"
      >
        +
      </button>
    </div>
  );
}
function FieldError({ message, id }: { message?: string; id?: string }) {
  return message ? (
    <p id={id} role="alert" className="mt-2 text-xs text-red-600">
      {message}
    </p>
  ) : null;
}

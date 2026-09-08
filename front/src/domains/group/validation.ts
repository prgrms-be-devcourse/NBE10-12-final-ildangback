import { z } from "zod";
import { CATEGORIES, categoryMap } from "./constants";

export const WEEKDAYS = [
  "MON",
  "TUE",
  "WED",
  "THU",
  "FRI",
  "SAT",
  "SUN",
] as const;
// Limits come from GroupCreateRequest / InitialChallengeSettingRequest, not entities.
export const GROUP_NAME_MAX = 100;
export const GROUP_DESCRIPTION_MAX = 2000;
export const groupCreateSchema = z
  .object({
    name: z
      .string()
      .min(1, "그룹명을 입력해주세요.")
      .max(GROUP_NAME_MAX, "그룹명은 100자 이하여야 합니다.")
      .refine((value) => value.trim().length > 0, "그룹명을 입력해주세요."),
    description: z
      .string()
      .max(GROUP_DESCRIPTION_MAX, "설명은 2,000자 이하여야 합니다."),
    category: z.enum(CATEGORIES),
    mapType: z.enum(["STUDY_ROOM", "GYM"]),
    visibility: z.enum(["PUBLIC", "CODE_ONLY"]),
    maxMembers: z.number().int().min(1).max(6),
    challenge: z.object({
      startDate: z.iso.date("시작일을 선택해주세요."),
      endDate: z.iso.date("종료일을 선택해주세요."),
      frequencyType: z.enum(["DAILY", "EVERY_N_DAYS", "DAYS_OF_WEEK"]),
      frequencyValue: z
        .number()
        .int()
        .min(2, "2~7일 사이로 선택해주세요.")
        .max(7, "2~7일 사이로 선택해주세요.")
        .nullable(),
      daysOfWeek: z.array(z.enum(WEEKDAYS)),
      dailyCheckInCount: z
        .number()
        .int()
        .min(1, "하루 최소 1회 인증해야 합니다.")
        .max(10, "하루 최대 10회까지 설정할 수 있어요."),
      allowedTypes: z
        .array(z.literal("PHOTO"))
        .length(1, "사진 인증을 선택해주세요."),
    }),
  })
  .superRefine((values, ctx) => {
    if (values.mapType !== categoryMap(values.category))
      ctx.addIssue({
        code: "custom",
        path: ["mapType"],
        message: "카테고리에 맞는 맵을 선택해주세요.",
      });
    const settings = values.challenge;
    if (settings.endDate < settings.startDate)
      ctx.addIssue({
        code: "custom",
        path: ["challenge", "endDate"],
        message: "종료일은 시작일보다 빠를 수 없어요.",
      });
    if (
      settings.frequencyType === "EVERY_N_DAYS" &&
      settings.frequencyValue === null
    )
      ctx.addIssue({
        code: "custom",
        path: ["challenge", "frequencyValue"],
        message: "인증 간격을 선택해주세요.",
      });
    if (
      settings.frequencyType === "DAYS_OF_WEEK" &&
      settings.daysOfWeek.length === 0
    )
      ctx.addIssue({
        code: "custom",
        path: ["challenge", "daysOfWeek"],
        message: "인증할 요일을 하나 이상 선택해주세요.",
      });
    // The server owns 'tomorrow' and business-day boundaries. START_DATE_INVALID
    // is returned to this field; don't implement a browser-local version of that policy.
  });
export type GroupCreateForm = z.infer<typeof groupCreateSchema>;
export const STEP_FIELDS = [
  ["category", "mapType"],
  ["name", "description", "challenge.startDate", "challenge.endDate"],
  [
    "challenge.frequencyType",
    "challenge.frequencyValue",
    "challenge.daysOfWeek",
    "challenge.dailyCheckInCount",
  ],
  ["challenge.allowedTypes"],
  ["visibility", "maxMembers"],
] as const;
export const CREATE_FIELDS = STEP_FIELDS.flat();

/** A duration shortcut for user input only, never a progress/business-day calculation. */
export function endDateForDuration(start: string, days: number): string | null {
  if (
    !z.iso.date().safeParse(start).success ||
    !Number.isInteger(days) ||
    days < 1
  )
    return null;
  const date = new Date(`${start}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days - 1);
  return Number.isFinite(date.getTime())
    ? date.toISOString().slice(0, 10)
    : null;
}

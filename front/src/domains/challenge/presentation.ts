import type { ChallengeSettings, DaysOfWeek } from "./types";

const DAYS: Record<DaysOfWeek, string> = {
  MON: "월",
  TUE: "화",
  WED: "수",
  THU: "목",
  FRI: "금",
  SAT: "토",
  SUN: "일",
};
export function frequencyLabel(
  settings: Pick<
    ChallengeSettings,
    "frequencyType" | "frequencyValue" | "daysOfWeek"
  >,
) {
  if (settings.frequencyType === "DAILY") return "매일";
  if (settings.frequencyType === "EVERY_N_DAYS")
    return `${settings.frequencyValue}일마다`;
  return (settings.daysOfWeek ?? []).map((day) => DAYS[day]).join(" · ");
}

import type { GroupCategory, MapType } from "./types";

export const CATEGORIES = [
  "DEV",
  "READING",
  "JOB",
  "STUDY",
  "EXERCISE",
  "HEALTH",
  "LIFE",
  "ETC",
] as const;
export const CATEGORY_LABEL: Record<GroupCategory, string> = {
  DEV: "개발",
  READING: "독서",
  JOB: "취업",
  STUDY: "스터디",
  EXERCISE: "운동",
  HEALTH: "건강",
  LIFE: "생활",
  ETC: "기타",
};
export function categoryMap(category: GroupCategory): MapType {
  return category === "EXERCISE" || category === "HEALTH"
    ? "GYM"
    : "STUDY_ROOM";
}

// 목 데이터. 실 API 가 붙으면 이 폴더는 통째로 지운다.
//
// 키는 백엔드 GroupCategory 와 같다.

export type Category =
  "EXERCISE" | "STUDY" | "READING" | "HEALTH" | "ETC" | "LIFE" | "JOB" | "DEV";

export const CATEGORY_LABEL: Record<Category, string> = {
  EXERCISE: "운동",
  STUDY: "공부",
  READING: "독서",
  HEALTH: "건강",
  ETC: "기타",
  LIFE: "생활",
  JOB: "취업",
  DEV: "개발",
};

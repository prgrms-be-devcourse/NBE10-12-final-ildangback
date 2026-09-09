import type { Icon } from "@phosphor-icons/react";
import {
  BriefcaseIcon,
  DotsThreeCircleIcon,
  HeartbeatIcon,
  HouseLineIcon,
  PencilSimpleLineIcon,
} from "@phosphor-icons/react";
import type { Category } from "../../mocks/categories";
import { designArt } from "./designArt";

// 시안에 그림이 있는 카테고리는 그림을 쓴다. 나머지는 phosphor 로 채운다.
export const CATEGORY_ART: Partial<Record<Category, string>> = {
  EXERCISE: designArt.categoryExercise,
  DEV: designArt.categoryDev,
  READING: designArt.categoryReading,
};

export const CATEGORY_ICON: Record<Category, Icon> = {
  EXERCISE: HeartbeatIcon,
  STUDY: PencilSimpleLineIcon,
  READING: DotsThreeCircleIcon,
  HEALTH: HeartbeatIcon,
  ETC: DotsThreeCircleIcon,
  LIFE: HouseLineIcon,
  JOB: BriefcaseIcon,
  DEV: DotsThreeCircleIcon,
};

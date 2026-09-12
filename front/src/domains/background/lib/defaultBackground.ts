// 아직 배경을 안 산 그룹이 보는 기본 그림. 실제 판매 배경(관리자가 올린 것)과는
// 별개다 — 서버 응답의 imageUrl 이 null 일 때만 프론트가 이걸로 채운다.
import gymDefault from "../../../assets/backgrounds/gym-default.webp";
import studyRoomDefault from "../../../assets/backgrounds/study-room-default.webp";
import type { MapType } from "../types";

export const MAP_TYPE_DEFAULT_IMAGE: Record<MapType, string> = {
  STUDY_ROOM: studyRoomDefault,
  GYM: gymDefault,
};

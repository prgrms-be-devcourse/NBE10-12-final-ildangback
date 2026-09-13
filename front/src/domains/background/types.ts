import type { MapType } from "../group/types";

export type { MapType };

export const MAP_TYPE_LABEL: Record<MapType, string> = {
  STUDY_ROOM: "스터디룸",
  GYM: "운동",
};

export type PurchaseRequestStatus = "VOTING" | "APPROVED" | "REJECTED";

export interface BackgroundResponse {
  backgroundId: number;
  mapType: MapType;
  name: string;
  imageUrl: string;
  price: number;
}

export interface ShopBackgroundResponse {
  backgroundId: number;
  name: string;
  imageUrl: string;
  price: number;
  owned: boolean;
  active: boolean;
  voting: boolean;
}

/** backgroundId 와 imageUrl 이 null 이면 아직 아무 배경도 사지 않은 그룹이다. */
export interface GroupBackgroundResponse {
  backgroundId: number | null;
  name: string | null;
  imageUrl: string | null;
  mapType: MapType;
}

export interface PurchaseRequestResponse {
  requestId: number;
  backgroundId: number;
  backgroundName: string;
  imageUrl: string;
  price: number;
  requestedBy: number;
  requestedByNickname: string | null;
  status: PurchaseRequestStatus;
  agreeCount: number;
  disagreeCount: number;
  totalMembers: number;
  requiredCount: number;
  myAgreed: boolean | null;
  expiresAt: string;
}

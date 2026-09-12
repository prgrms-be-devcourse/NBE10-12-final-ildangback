import { apiFetch } from "../../shared/api/client";
import { getGroup } from "../group/api";
import type { GroupDetailResponse, SliceResponse } from "../group/types";
import { getGroupBalance } from "../point/api";
import type {
  BackgroundResponse,
  GroupBackgroundResponse,
  MapType,
  PurchaseRequestResponse,
  ShopBackgroundResponse,
} from "./types";

// 컨트롤러가 @Max(100) 으로 막는 한 번 최대치다.
const PAGE_SIZE = 100;

export function getShopBackgrounds(
  groupId: number,
  owned?: boolean,
  cursor?: number,
): Promise<SliceResponse<ShopBackgroundResponse>> {
  const params = new URLSearchParams({ size: String(PAGE_SIZE) });
  if (owned !== undefined) params.set("owned", String(owned));
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return apiFetch(`/api/groups/${groupId}/shop/backgrounds?${params}`);
}

/**
 * 그룹의 맵 타입 계열 배경 전체. 커서를 끝까지 따라간다.
 *
 * 화면이 보유 필터를 브라우저에서 거르므로 목록이 다 있어야 한다.
 */
export async function getAllShopBackgrounds(
  groupId: number,
): Promise<ShopBackgroundResponse[]> {
  const all: ShopBackgroundResponse[] = [];
  let cursor: number | undefined;

  for (;;) {
    const page = await getShopBackgrounds(groupId, undefined, cursor);
    all.push(...page.content);

    const next = page.hasNext ? page.nextCursor : null;
    if (next === null || (cursor !== undefined && next <= cursor)) return all;
    cursor = next;
  }
}

export function getActiveBackground(
  groupId: number,
): Promise<GroupBackgroundResponse> {
  return apiFetch(`/api/groups/${groupId}/background`);
}

export function applyBackground(
  groupId: number,
  backgroundId: number,
): Promise<GroupBackgroundResponse> {
  return apiFetch(`/api/groups/${groupId}/background`, {
    method: "PATCH",
    body: { backgroundId },
  });
}

export function createPurchaseRequest(
  groupId: number,
  backgroundId: number,
): Promise<PurchaseRequestResponse> {
  return apiFetch(`/api/groups/${groupId}/shop/purchase-requests`, {
    method: "POST",
    body: { backgroundId },
  });
}

/** 진행 중인 제안이 없으면 204 라 undefined 가 온다. */
export function getCurrentPurchaseRequest(
  groupId: number,
): Promise<PurchaseRequestResponse | undefined> {
  return apiFetch(`/api/groups/${groupId}/shop/purchase-requests/current`);
}

export function voteOnPurchaseRequest(
  groupId: number,
  requestId: number,
  agreed: boolean,
): Promise<PurchaseRequestResponse> {
  return apiFetch(
    `/api/groups/${groupId}/shop/purchase-requests/${requestId}/votes`,
    { method: "POST", body: { agreed } },
  );
}

export function cancelPurchaseRequest(
  groupId: number,
  requestId: number,
): Promise<void> {
  return apiFetch(
    `/api/groups/${groupId}/shop/purchase-requests/${requestId}`,
    { method: "DELETE" },
  );
}

export interface GroupShopData {
  group: GroupDetailResponse;
  catalog: ShopBackgroundResponse[];
  active: GroupBackgroundResponse;
  current: PurchaseRequestResponse | null;
  balance: number;
}

/** 그룹 상점 화면 한 벌. 그룹 정보, 카탈로그, 적용된 배경, 진행 중인 제안, 그룹 포인트를 합친다. */
export async function fetchGroupShop(groupId: number): Promise<GroupShopData> {
  const [group, catalog, active, current, { balance }] = await Promise.all([
    getGroup(groupId),
    getAllShopBackgrounds(groupId),
    getActiveBackground(groupId),
    getCurrentPurchaseRequest(groupId),
    getGroupBalance(groupId),
  ]);
  return { group, catalog, active, current: current ?? null, balance };
}

// ===== 관리자 =====

export function getBackgrounds(
  cursor?: number,
): Promise<SliceResponse<BackgroundResponse>> {
  const params = new URLSearchParams({ size: String(PAGE_SIZE) });
  if (cursor !== undefined) params.set("cursor", String(cursor));
  return apiFetch(`/api/admin/backgrounds?${params}`);
}

/** 관리자 목록용 카탈로그 전체. 커서를 끝까지 따라간다. */
export async function getAllBackgrounds(): Promise<BackgroundResponse[]> {
  const all: BackgroundResponse[] = [];
  let cursor: number | undefined;

  for (;;) {
    const page = await getBackgrounds(cursor);
    all.push(...page.content);

    const next = page.hasNext ? page.nextCursor : null;
    if (next === null || (cursor !== undefined && next <= cursor)) return all;
    cursor = next;
  }
}

export interface CreateBackgroundInput {
  mapType: MapType;
  name: string;
  price: number;
  image: File;
}

/** POST /api/admin/backgrounds (multipart/form-data, 관리자) */
export function createBackground(
  input: CreateBackgroundInput,
): Promise<BackgroundResponse> {
  const form = new FormData();
  form.append("mapType", input.mapType);
  form.append("name", input.name);
  form.append("price", String(input.price));
  form.append("image", input.image, input.image.name);
  return apiFetch("/api/admin/backgrounds", { method: "POST", body: form });
}

/** DELETE /api/admin/backgrounds/{backgroundId} (관리자) */
export function deleteBackground(backgroundId: number): Promise<void> {
  return apiFetch(`/api/admin/backgrounds/${backgroundId}`, {
    method: "DELETE",
  });
}

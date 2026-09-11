import { apiFetch } from "../../shared/api/client";
import type {
  CharacterResponse,
  ItemPurchaseResponse,
  ItemSlot,
  ShopItemResponse,
  SliceResponse,
  UserItemResponse,
} from "../../shared/api/types";
import { getMyBalance } from "../point/api";
import type { ShopData } from "./lib/shop";

// 컨트롤러가 @Max(100) 으로 막는 한 번 최대치다.
const PAGE_SIZE = 100;

export interface ItemPageParams {
  slot?: ItemSlot;
  cursor?: number | null;
  size?: number;
}

function itemQuery(params: ItemPageParams): string {
  const query = new URLSearchParams();
  if (params.slot) query.set("slot", params.slot);
  if (params.cursor != null) query.set("cursor", String(params.cursor));
  if (params.size != null) query.set("size", String(params.size));

  const queryString = query.toString();
  return queryString ? `?${queryString}` : "";
}

export function getShopItems(
  params: ItemPageParams = {},
): Promise<SliceResponse<ShopItemResponse>> {
  return apiFetch(`/api/items${itemQuery(params)}`);
}

export function getMyItems(
  params: ItemPageParams = {},
): Promise<SliceResponse<UserItemResponse>> {
  return apiFetch(`/api/users/me/items${itemQuery(params)}`);
}

/**
 * 착용 중인 부위의 그림. 상점은 이미 카탈로그를 들고 있어서 쓰지 않고,
 * 목록이 없는 화면(프로필)이 캐릭터를 그릴 때 쓴다.
 */
export function getMyCharacter(): Promise<CharacterResponse> {
  return apiFetch("/api/users/me/character");
}

export function purchaseItem(itemId: number): Promise<ItemPurchaseResponse> {
  return apiFetch(`/api/items/${itemId}/purchase`, { method: "POST" });
}

export function equipItem(userItemId: number): Promise<UserItemResponse> {
  return apiFetch(`/api/users/me/items/${userItemId}/equip`, { method: "PUT" });
}

export function unequipItem(userItemId: number): Promise<UserItemResponse> {
  return apiFetch(`/api/users/me/items/${userItemId}/equip`, {
    method: "DELETE",
  });
}

/**
 * 커서 페이징을 끝까지 따라가서 한 배열로 만든다.
 *
 * 화면이 부위 탭과 보유 필터와 정렬을 전부 브라우저에서 거르기 때문에 목록이
 * 다 있어야 한다. hasNext 가 true 여도 nextCursor 가 비면 멈춘다.
 */
async function fetchAllPages<T>(
  load: (cursor: number | null) => Promise<SliceResponse<T>>,
): Promise<T[]> {
  const all: T[] = [];
  let cursor: number | null = null;

  do {
    const page = await load(cursor);
    all.push(...page.content);
    cursor = page.hasNext ? page.nextCursor : null;
  } while (cursor !== null);

  return all;
}

/**
 * 상점 화면 한 벌. 카탈로그, 보유 목록, 포인트 잔액을 합친다.
 *
 * 보유 여부와 착용 상태는 보유 목록 하나에서만 읽는다. 상점 응답에도 owned 와
 * equipped 가 있지만 착용과 해제에 필요한 userItemId 는 보유 목록에만 있어서,
 * 두 곳을 섞으면 응답 시점이 어긋났을 때 화면이 갈린다.
 */
export async function fetchShop(): Promise<ShopData> {
  const [catalog, mine, balance] = await Promise.all([
    fetchAllPages((cursor) => getShopItems({ cursor, size: PAGE_SIZE })),
    fetchAllPages((cursor) => getMyItems({ cursor, size: PAGE_SIZE })),
    getMyBalance(),
  ]);

  const ownedByItemId = new Map(
    mine.map((userItem) => [userItem.item.id, userItem]),
  );

  const equipped: ShopData["equipped"] = {};
  for (const userItem of mine) {
    if (userItem.equippedSlot)
      equipped[userItem.equippedSlot] = userItem.item.id;
  }

  return {
    point: balance.balance,
    equipped,
    items: catalog.map(({ item }) => {
      const owned = ownedByItemId.get(item.id);
      return {
        id: item.id,
        slot: item.slot,
        name: item.name,
        imageUrl: item.imageUrl,
        price: item.price,
        owned: owned !== undefined,
        userItemId: owned?.id ?? null,
      };
    }),
  };
}

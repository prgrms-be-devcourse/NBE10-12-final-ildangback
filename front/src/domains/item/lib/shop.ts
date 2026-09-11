// 개인 상점 화면의 표시 값과 화면이 다루는 모양.
//
// 부위는 백엔드 ItemSlot 을 그대로 쓴다. 보유 필터와 정렬은 서버에 없는
// 프론트 전용 값이라 여기서만 산다.

import type { ItemSlot } from "../../../shared/api/types";

export const SLOTS: readonly ItemSlot[] = ["HEAD", "TOP", "BOTTOM", "SHOES"];

export const SLOT_LABEL: Record<ItemSlot, string> = {
  HEAD: "모자",
  TOP: "상의",
  BOTTOM: "하의",
  SHOES: "신발",
};

export const OWNERSHIP_FILTERS = ["ALL", "OWNED", "NOT_OWNED"] as const;
export type OwnershipFilter = (typeof OWNERSHIP_FILTERS)[number];

export const OWNERSHIP_LABEL: Record<OwnershipFilter, string> = {
  ALL: "전체",
  OWNED: "보유 중",
  NOT_OWNED: "미보유",
};

// 서버가 주는 순서는 등록순 하나다. DEFAULT 는 받은 순서를 그대로 둔다.
export const SORTS = ["DEFAULT", "CHEAP", "EXPENSIVE"] as const;
export type Sort = (typeof SORTS)[number];

export const SORT_LABEL: Record<Sort, string> = {
  DEFAULT: "기본순",
  CHEAP: "낮은 가격순",
  EXPENSIVE: "높은 가격순",
};

/**
 * 캐릭터 미리보기에 겹쳐 그리는 순서. 뒤에 오는 것이 위로 올라간다.
 *
 * 아이템 그림은 몸통과 같은 캔버스를 쓰는 전체 프레임 그림이다. 서버가 좌표를
 * 주지 않고 포즈별로 그림을 한 장씩 갖는 구조라 위치가 그림에 박혀 있다.
 */
export const CHARACTER_LAYERS: readonly ItemSlot[] = [
  "SHOES",
  "BOTTOM",
  "TOP",
  "HEAD",
];

/**
 * 캐릭터 응답의 slots 에서 겹쳐 그릴 그림만 남긴다.
 * 서버는 안 낀 부위도 null 로 채워 보낸다.
 */
export function toCharacterArt(
  slots: Record<ItemSlot, string | null>,
): Partial<Record<ItemSlot, string>> {
  const art: Partial<Record<ItemSlot, string>> = {};
  for (const slot of CHARACTER_LAYERS) {
    const url = slots[slot];
    if (url) art[slot] = url;
  }
  return art;
}

/**
 * 카드와 확인 창의 썸네일은 전체 프레임 그림을 그대로 쓰면 아이템이 작게 박힌다.
 * 부위마다 그림에서 차지하는 자리가 정해져 있으니 그 자리를 확대해 보여준다.
 *
 * 실제 아이템 그림이 들어오면 이 숫자만 맞추면 된다.
 */
export const SLOT_THUMB: Record<ItemSlot, { scale: number; origin: string }> = {
  HEAD: { scale: 2.3, origin: "50% 19.3%" },
  TOP: { scale: 2.87, origin: "50% 59.3%" },
  BOTTOM: { scale: 2.87, origin: "50% 74.8%" },
  SHOES: { scale: 2.39, origin: "50% 86.3%" },
};

/** 화면이 그리는 아이템 한 줄. 상점 카탈로그와 보유 목록을 합친 결과다. */
export interface ShopItem {
  id: number;
  slot: ItemSlot;
  name: string;
  imageUrl: string | null;
  price: number;
  owned: boolean;
  /** 보유 중일 때만 있다. 착용과 해제에 쓴다. */
  userItemId: number | null;
}

export interface ShopData {
  point: number;
  items: ShopItem[];
  /** 부위별로 착용 중인 itemId. */
  equipped: Partial<Record<ItemSlot, number>>;
}

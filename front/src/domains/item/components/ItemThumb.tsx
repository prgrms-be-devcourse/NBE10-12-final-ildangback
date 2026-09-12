import { SLOT_THUMB, type ShopItem } from "../lib/shop";

/**
 * 아이템 한 개의 썸네일. 부모를 꽉 채운다.
 *
 * 그림이 캐릭터 전체 프레임이라 그대로 두면 아이템이 한구석에 작게 남는다.
 * 부위별로 그 자리를 확대해서 잘라 보여준다.
 */
export function ItemThumb({ item }: { item: ShopItem }) {
  if (!item.imageUrl) return null;

  const { scale, origin } = SLOT_THUMB[item.slot];

  // 확대 기준점이 상자 기준이라 상자가 정사각이 아니면 가로 세로가 따로 논다.
  // 그림이 1080x1080 이므로 부르는 쪽이 정사각 상자를 준다.
  return (
    <span className="relative block h-full w-full overflow-hidden">
      <img
        src={item.imageUrl}
        alt=""
        className="absolute inset-0 h-full w-full object-contain pixelated"
        style={{ transform: `scale(${scale})`, transformOrigin: origin }}
      />
    </span>
  );
}

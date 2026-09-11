import type { ItemSlot } from "../../../shared/api/types";
import { designArt } from "../../../shared/ui/designArt";
import { CHARACTER_LAYERS } from "../lib/shop";

/**
 * 기본 몸통 위에 착용 중인 아이템을 겹쳐 그린다.
 *
 * 아이템 그림은 몸통과 같은 캔버스를 쓰는 전체 프레임 그림이라 좌표를 따로 주지
 * 않는다. 크기와 자리는 부르는 쪽이 정한다 - 이 컴포넌트는 부모를 꽉 채운다.
 */
export function CharacterView({
  art,
  label,
}: {
  art: Partial<Record<ItemSlot, string>>;
  label: string;
}) {
  return (
    <span
      className="relative block h-full w-full"
      role="img"
      aria-label={label}
    >
      <img
        src={designArt.characterBase}
        alt=""
        className="absolute inset-0 h-full w-full object-contain pixelated"
        aria-hidden
      />
      {CHARACTER_LAYERS.map((slot) =>
        art[slot] ? (
          <img
            key={slot}
            src={art[slot]}
            alt=""
            className="absolute inset-0 h-full w-full object-contain pixelated"
            aria-hidden
          />
        ) : null,
      )}
    </span>
  );
}

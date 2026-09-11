import type { ItemSlot } from "../../../shared/api/types";
import { designArt } from "../../../shared/ui/designArt";
import { CHARACTER_LAYERS } from "../lib/shop";

/**
 * 기본 몸통 위에 착용 중인 아이템을 겹쳐 그린다.
 *
 * 아이템 그림은 몸통과 같은 캔버스를 쓰는 전체 프레임 그림이라 좌표를 따로 주지
 * 않는다. 크기와 자리는 부르는 쪽이 정한다 - 이 컴포넌트는 부모를 꽉 채운다.
 */
/**
 * 그림 안에서 캐릭터가 차지하는 자리. 1080x1080 캔버스에서 실측한 값이다.
 * 여백이 넓어서 작은 상자에 그대로 넣으면 캐릭터가 작아 보인다.
 */
const TIGHT_SCALE = 1.3;
const TIGHT_ORIGIN = "50% 52%";

export function CharacterView({
  art,
  label,
  fit = "frame",
}: {
  art: Partial<Record<ItemSlot, string>>;
  label: string;
  /**
   * frame - 캔버스를 그대로 쓴다. 배경판 위에 세울 때처럼 자리가 중요한 경우.
   * tight - 위아래 여백을 잘라 캐릭터를 키운다. 작은 상자에 넣을 때.
   */
  fit?: "frame" | "tight";
}) {
  const zoom =
    fit === "tight"
      ? { transform: `scale(${TIGHT_SCALE})`, transformOrigin: TIGHT_ORIGIN }
      : undefined;

  return (
    <span
      className="relative block h-full w-full overflow-hidden"
      role="img"
      aria-label={label}
    >
      <img
        src={designArt.characterBase}
        alt=""
        className="absolute inset-0 h-full w-full object-contain pixelated"
        style={zoom}
        aria-hidden
      />
      {CHARACTER_LAYERS.map((slot) =>
        art[slot] ? (
          <img
            key={slot}
            src={art[slot]}
            alt=""
            className="absolute inset-0 h-full w-full object-contain pixelated"
            style={zoom}
            aria-hidden
          />
        ) : null,
      )}
    </span>
  );
}

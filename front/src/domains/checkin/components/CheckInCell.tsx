import { PlayIcon } from "@phosphor-icons/react";
import { badgeColor, nicknameInitial } from "../lib/authorBadge";
import type { CheckIn } from "../types";

interface Props {
  item: CheckIn;
  onSelect: () => void;
  /** 작성자 이니셜 뱃지 표시. 갤러리 탭만 — 프로필 모아보기는 본인 것이라 생략. */
  showAuthor?: boolean;
}

/** 인증 그리드 한 칸. 탭하면 라이트박스로 확대된다. */
export function CheckInCell({ item, onSelect, showAuthor = false }: Props) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className="relative aspect-square overflow-hidden rounded-md bg-gray-100"
    >
      <img
        src={item.mediaUrl}
        alt={`${item.nickname}의 인증`}
        loading="lazy"
        className="size-full object-cover"
      />

      {item.mediaType === "VIDEO" && (
        <span className="absolute top-1.5 right-1.5 flex size-6 items-center justify-center rounded-full bg-black/50 text-white">
          <PlayIcon size={12} weight="fill" />
        </span>
      )}

      {showAuthor && (
        <span className="absolute bottom-1.5 left-1.5 flex items-center gap-1.5 rounded-full bg-black/45 py-1 pr-2.5 pl-1">
          <span
            className="flex size-[22px] items-center justify-center rounded-full text-[11px] font-bold text-white"
            style={{ backgroundColor: badgeColor(item.userId) }}
          >
            {nicknameInitial(item.nickname)}
          </span>
          <span className="max-w-[84px] truncate text-[13px] font-semibold text-white">
            {item.nickname}
          </span>
        </span>
      )}
    </button>
  );
}

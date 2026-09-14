import { PlayIcon } from "@phosphor-icons/react";
import { AuthedImage } from "../../../shared/ui/AuthedImage";
import { badgeColor, nicknameInitial } from "../lib/authorBadge";
import type { CheckIn } from "../types";

interface Props {
  item: CheckIn;
  onSelect: () => void;
  /** 작성자 이니셜 뱃지 표시. 갤러리 탭만 — 프로필 모아보기는 본인 것이라 생략. */
  showAuthor?: boolean;
}

/**
 * 인증 그리드 한 칸. 탭하면 라이트박스로 확대된다.
 *
 * 영상 체크인은 원본(`mediaUrl`)이 아니라 서버가 만든 정지 썸네일(`posterUrl`)을 그린다 —
 * 그리드 칸 9개가 동시에 영상 스트림을 열면 무겁고, `<img>` 는 애초에 영상을 재생하지도
 * 못한다(과거엔 mediaUrl 을 그대로 박아 실제로는 안 뜨는 버그였다).
 */
export function CheckInCell({ item, onSelect, showAuthor = false }: Props) {
  const isVideo = item.mediaType === "VIDEO";
  const thumbnailUrl = isVideo ? item.posterUrl : item.mediaUrl;

  return (
    <button
      type="button"
      onClick={onSelect}
      className="relative aspect-square overflow-hidden rounded-md bg-gray-100"
    >
      <AuthedImage
        src={thumbnailUrl}
        alt={`${item.nickname}의 인증`}
        className="size-full object-cover"
      />

      {isVideo && (
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

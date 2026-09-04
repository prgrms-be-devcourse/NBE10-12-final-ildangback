import { useToast } from "../../../shared/lib/useToast";
import type { CheckInType } from "../types";
import { Chip } from "./Chip";

interface Props {
  value: CheckInType | null;
  onChange: (value: CheckInType | null) => void;
}

/**
 * 인증 유형 필터 칩 (전체 / 사진 / 영상 / 라이브). 시안이 4개를 명시한다.
 * `CheckInType` enum 은 `PHOTO` 만이라 영상·라이브는 회색 + 탭 시 안내 토스트.
 * VIDEO enum 이 들어오면 두 칩을 활성화만 하면 된다.
 */
export function CheckInTypeChips({ value, onChange }: Props) {
  const { showToast } = useToast();

  return (
    <div className="flex gap-2 overflow-x-auto pb-1">
      <Chip active={value === null} onClick={() => onChange(null)}>
        전체
      </Chip>
      <Chip active={value === "PHOTO"} onClick={() => onChange("PHOTO")}>
        사진
      </Chip>
      <button
        type="button"
        onClick={() => showToast("영상 인증은 아직 지원하지 않아요")}
        className="shrink-0 rounded-full bg-gray-100 px-3 py-1 text-[13px] font-medium text-gray-300"
      >
        영상
      </button>
      <button
        type="button"
        onClick={() => showToast("라이브 인증은 아직 지원하지 않아요")}
        className="shrink-0 rounded-full bg-gray-100 px-3 py-1 text-[13px] font-medium text-gray-300"
      >
        라이브
      </button>
    </div>
  );
}

import { useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { useCheckInGallery } from "../lib/useCheckInGallery";
import { useChallengeMembers } from "../lib/useChallengeMembers";
import { currentMonth } from "../lib/month";
import type { CheckIn } from "../types";
import { Chip } from "./Chip";
import { CheckInGrid, CheckInGridSkeleton } from "./CheckInGrid";
import { CheckInLightbox } from "./CheckInLightbox";
import { MonthNav } from "./MonthNav";

/**
 * 챌린지 상세 > 갤러리 탭. 시안: front/docs/checkin-gallery-wireframe/gallery-tab.png
 *
 * 월 네비 + 참여자 필터 칩 + 3열 평평한 그리드(커서 무한스크롤). 셀을 탭하면 라이트박스로
 * 확대되고 사진 아래에 시간·memo·작성자가 뜬다.
 */
export function CheckInGalleryTab({ challengeId }: { challengeId: number }) {
  const [month, setMonth] = useState(currentMonth);
  const [userId, setUserId] = useState<number | null>(null);
  const [selected, setSelected] = useState<CheckIn | null>(null);

  const members = useChallengeMembers(challengeId);
  const { items, loading, loadingMore, error, hasNext, loadMore, reload } =
    useCheckInGallery(challengeId, { month, userId });

  return (
    <div className="px-4 pt-4 pb-10">
      <MonthNav month={month} onChange={setMonth} />

      <div className="mt-3 flex gap-2 overflow-x-auto pb-1">
        <Chip active={userId === null} onClick={() => setUserId(null)}>
          전체
        </Chip>
        {members.map((m) => (
          <Chip
            key={m.userId}
            active={userId === m.userId}
            onClick={() => setUserId(m.userId)}
          >
            {m.nickname}
          </Chip>
        ))}
      </div>

      <div className="mt-4">
        {loading ? (
          <CheckInGridSkeleton />
        ) : error ? (
          <div className="py-16 text-center">
            <p className="text-[14px] text-gray-500">불러오지 못했어요</p>
            <Button variant="secondary" className="mt-4" onClick={reload}>
              다시 시도
            </Button>
          </div>
        ) : items.length === 0 ? (
          <p className="py-16 text-center text-[14px] text-gray-400">
            {userId === null
              ? "이번 달 인증이 없어요"
              : "조건에 맞는 인증이 없어요"}
          </p>
        ) : (
          <CheckInGrid
            items={items}
            showAuthor
            onSelect={setSelected}
            hasNext={hasNext}
            loadingMore={loadingMore}
            onLoadMore={loadMore}
          />
        )}
      </div>

      <CheckInLightbox
        item={selected}
        onClose={() => setSelected(null)}
        showAuthor
      />
    </div>
  );
}

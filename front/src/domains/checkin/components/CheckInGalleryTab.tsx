import { useState } from "react";
import { accessTokenUserId } from "../../../shared/lib/accessTokenRole";
import { useToast } from "../../../shared/lib/useToast";
import {
  clampToRange,
  currentMonth,
  monthRangeOf,
  type DatePeriod,
} from "../lib/month";
import { useChallengeMembers } from "../lib/useChallengeMembers";
import { useCheckInGallery } from "../lib/useCheckInGallery";
import type { ChallengeMember, CheckIn } from "../types";
import { CheckInGridSection } from "./CheckInGridSection";
import { CheckInLightbox } from "./CheckInLightbox";
import { Chip } from "./Chip";
import { MonthNav } from "./MonthNav";

/**
 * 챌린지 상세 > 갤러리 탭. 시안: front/docs/checkin-gallery-wireframe/gallery-tab.png
 *
 * 월 네비 + 참여자 필터 칩 + 3열 평평한 그리드(커서 무한스크롤). 셀을 탭하면 라이트박스로
 * 확대되고 사진 아래에 시간·memo·작성자가 뜬다.
 */
export function CheckInGalleryTab({
  challengeId,
  members: preloadedMembers,
  period,
}: {
  challengeId: number;
  members?: ChallengeMember[] | null;
  /** 챌린지 시작·종료일. 기록이 없는 달로 못 넘어가게 월 네비 범위를 좁히고, 초기 진입 달도 그 범위 안으로 당겨온다. */
  period?: DatePeriod;
}) {
  const { showToast } = useToast();
  const { minMonth, maxMonth } = monthRangeOf(period);
  const [month, setMonth] = useState(() =>
    clampToRange(currentMonth(), minMonth, maxMonth),
  );
  const [userId, setUserId] = useState<number | null>(null);
  const [selected, setSelected] = useState<CheckIn | null>(null);

  const fetchedMembers = useChallengeMembers(
    challengeId,
    preloadedMembers == null,
  );
  const members = preloadedMembers ?? fetchedMembers;
  const result = useCheckInGallery(challengeId, { month, userId });

  return (
    <div className="pt-2">
      <MonthNav
        month={month}
        onChange={setMonth}
        minMonth={minMonth}
        maxMonth={maxMonth}
      />

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
        <CheckInGridSection
          result={result}
          emptyMessage={
            userId === null
              ? "이번 달 인증이 없어요"
              : "조건에 맞는 인증이 없어요"
          }
          onSelect={setSelected}
          showAuthor
        />
      </div>

      <CheckInLightbox
        item={selected}
        onClose={() => setSelected(null)}
        showAuthor
        currentUserId={accessTokenUserId()}
        onReported={() => showToast("신고를 접수했어요.")}
      />
    </div>
  );
}

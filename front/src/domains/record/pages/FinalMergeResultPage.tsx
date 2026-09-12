import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { getChallengeMergeOverview, getFinalMergeDetail } from "../api";
import { MergeResultView } from "../components/MergeResultView";
import { useAuth } from "../../../shared/lib/useAuth";
import { TopBar } from "../../../shared/ui/TopBar";
import type { FinalMergeDetailResponse } from "../../../shared/api/types";

export function FinalMergeResultPage() {
  const { challengeId } = useParams<{ challengeId: string }>();
  const { user } = useAuth();
  const [merge, setMerge] = useState<FinalMergeDetailResponse | null>(null);
  const [groupName, setGroupName] = useState<string | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!challengeId) return;
    let cancelled = false;

    Promise.all([
      getFinalMergeDetail(Number(challengeId)),
      getChallengeMergeOverview(Number(challengeId)),
    ])
      .then(([detail, overview]) => {
        if (cancelled) return;
        setMerge(detail);
        setGroupName(overview.groupName);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, [challengeId]);

  return (
    <>
      <TopBar title="챌린지 결과" />

      {error && (
        <p className="py-10 text-center text-[13px] text-gray-500">
          존재하지 않는 최종 머지예요.
        </p>
      )}

      {!error && !merge && (
        <p className="py-10 text-center text-[13px] text-gray-500">
          불러오는 중…
        </p>
      )}

      {merge && user && (
        <MergeResultView
          groupName={groupName ?? `챌린지 #${challengeId}`}
          // 최종 머지는 챌린지당 1개뿐이라 번호를 붙이지 않는다.
          badgeLabel="CHALLENGE MERGE"
          periodStart={merge.periodStart}
          periodEnd={merge.periodEnd}
          totalDays={merge.totalDays}
          totalCheckInCount={merge.totalCheckInCount}
          averageCompletionRate={merge.averageCompletionRate}
          participants={merge.participants}
          currentUserId={user.id}
          summaryDescription={`${merge.totalDays}일의 기록을 하나로 합쳤어요`}
          myRecordSectionTitle="나의 기여 기록"
          myProgressLabel="내가 인증한 날"
          trendChartTitle="월별 인증 추이"
          saveButtonLabel="결과 저장하기"
        />
      )}
    </>
  );
}

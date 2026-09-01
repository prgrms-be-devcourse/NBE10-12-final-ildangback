import { useState } from "react";
import { useNavigate, useParams } from "react-router";
import { CheckInMethodSheet } from "../domains/checkin/components/CheckInMethodSheet";
import { useTodayCheckInStatus } from "../domains/checkin/lib/useTodayCheckInStatus";
import { Button } from "../shared/ui/Button";
import { TopBar } from "../shared/ui/TopBar";

/**
 * ⚠️ 임시 스텁 — 체크인 플로우를 실제로 띄워보기 위한 최소 챌린지 상세 화면.
 *
 * 실제 챌린지 도메인 상세 화면이 들어오면 이 파일과 App.tsx 의 `/challenges/:challengeId`
 * 라우트, ChallengeTabPlaceholder 의 임시 링크를 함께 지운다.
 * 체크인 쪽에서 필요한 건 (1) `challengeId` 라우트 파라미터 (2) `CheckInMethodSheet` 를
 * 여는 "오늘 인증하기" 버튼 두 가지뿐이다.
 */
export function StubChallengeDetailPage() {
  const navigate = useNavigate();
  const { challengeId } = useParams();
  const [sheetOpen, setSheetOpen] = useState(false);
  const { status, loading, error } = useTodayCheckInStatus(Number(challengeId));

  const blocked = status ? status.completed || !status.isCheckInDay : false;
  const blockedReason = status?.completed
    ? "오늘 인증을 모두 마쳤어요"
    : "오늘은 인증하는 날이 아니에요";

  return (
    <>
      <TopBar title="챌린지 상세 (임시)" />

      <div className="flex-1 px-6 pt-4">
        <p className="text-[13px] font-semibold text-purple-500">오운완</p>
        <h1 className="mt-1 text-[22px] font-bold text-gray-900">
          매일 2시간 운동하기
        </h1>
        <p className="mt-2 text-[13px] text-gray-500">
          Day 110 / 180 · 4명 참여
        </p>
        <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-purple-100">
          <div className="h-full w-[61%] rounded-full bg-purple-500" />
        </div>

        <div className="mt-10">
          <Button
            onClick={() => setSheetOpen(true)}
            disabled={loading || blocked}
          >
            오늘 인증하기
          </Button>
          {blocked && (
            <p className="mt-2 text-center text-[13px] text-gray-400">
              {blockedReason}
            </p>
          )}
        </div>
      </div>

      <CheckInMethodSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        loading={loading}
        error={error}
        status={status}
        onSelectPhoto={() => navigate(`/challenges/${challengeId}/check-in`)}
      />
    </>
  );
}

import { useState } from "react";
import { useNavigate, useParams } from "react-router";
import { CheckInMethodSheet } from "../domains/checkin/components/CheckInMethodSheet";
import { useTodayCheckInStatus } from "../domains/checkin/lib/useTodayCheckInStatus";
import { TopBar } from "../shared/ui/TopBar";
import { StubChallengeHeaderCard } from "./StubChallengeHeaderCard";

/**
 * ⚠️ 임시 스텁 — 체크인 플로우와 갤러리 탭을 띄우기 위한 최소 챌린지 상세 화면.
 *
 * 실제 챌린지 도메인 상세 화면이 들어오면 이 파일과 StubChallengeHeaderCard,
 * App.tsx 의 `/challenges/:challengeId` 라우트, ChallengeTabPlaceholder 의 임시 링크를
 * 함께 지운다.
 *
 * 이 셸이 하는 일:
 *   - 상단 카드(StubChallengeHeaderCard) + "오늘 인증하기" → CheckInMethodSheet
 *   - 현황 / 일일 로그 / 갤러리 3개 탭. 현황·일일 로그는 다른 도메인 몫이라 placeholder 다.
 *   - 갤러리 탭만 실제 구현(CheckInGalleryTab, 체크인 도메인).
 *
 * 실제 챌린지 상세가 붙을 때는 이 TABS 배열의 각 panel 만 실제 컴포넌트로 갈아끼우면
 * 된다. 갤러리 panel 은 그대로 두고 status·dailyLog 만 교체하는 형태가 목표다.
 */
type TabKey = "status" | "dailyLog" | "gallery";

const TABS: { key: TabKey; label: string }[] = [
  { key: "status", label: "현황" },
  { key: "dailyLog", label: "일일 로그" },
  { key: "gallery", label: "갤러리" },
];

export function StubChallengeDetailPage() {
  const navigate = useNavigate();
  const { challengeId } = useParams();
  const [sheetOpen, setSheetOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<TabKey>("status");
  const { status, loading, error } = useTodayCheckInStatus(Number(challengeId));

  const blocked = status ? status.completed || !status.isCheckInDay : false;
  const blockedReason = !blocked
    ? null
    : status?.completed
      ? "오늘 인증을 모두 마쳤어요"
      : "오늘은 인증하는 날이 아니에요";

  return (
    <>
      <TopBar title="오운완" />

      <StubChallengeHeaderCard
        onCheckIn={() => setSheetOpen(true)}
        checkInDisabled={loading || blocked}
        blockedReason={blockedReason}
      />

      <nav className="mt-5 flex border-b border-gray-100">
        {TABS.map((tab) => {
          const active = tab.key === activeTab;
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => setActiveTab(tab.key)}
              className={`flex-1 border-b-2 pb-2.5 text-[14px] font-semibold transition-colors ${
                active
                  ? "border-purple-500 text-purple-600"
                  : "border-transparent text-gray-400"
              }`}
            >
              {tab.label}
            </button>
          );
        })}
      </nav>

      <div className="flex flex-1 flex-col">
        {activeTab === "status" && <StubTabPanel label="현황" />}
        {activeTab === "dailyLog" && <StubTabPanel label="일일 로그" />}
        {activeTab === "gallery" && <StubTabPanel label="갤러리" />}
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

/** 다른 도메인 화면이 들어올 자리. 실제 컴포넌트로 교체된다. */
function StubTabPanel({ label }: { label: string }) {
  return (
    <p className="px-6 py-20 text-center text-[14px] text-gray-400">
      {label} 화면이 들어올 자리입니다
    </p>
  );
}

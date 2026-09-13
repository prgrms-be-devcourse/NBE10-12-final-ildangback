import { CharacterRenderer } from "../../user/components/CharacterRenderer";
import { useNavigate } from "react-router";
import { useToast } from "../../../shared/lib/useToast";
import { ChatIcon } from "../../../shared/ui/icons";
import { frequencyLabel } from "../presentation";
import { useState } from "react";
import studyMap from "../../../assets/icons/studyMap.webp";
import sportsMap from "../../../assets/icons/sportsMap.webp";
import people from "../../../assets/icons/people.webp";
import camera from "../../../assets/icons/iconoir_camera.webp";
import type { MapType } from "../../group/types";
import type {
  ChallengeStatusResponse,
  ChallengeCharacterResponse,
  MemberTodayStatusResponse,
} from "../types";
import { ChallengeProgress, StatusBadge } from "./ChallengeInfo";
import { ExtensionChoicePanel } from "./ExtensionChoicePanel";
import { CheckInGalleryTab } from "../../checkin/components/CheckInGalleryTab";
import { DailyLogTimeline } from "../../checkin/components/DailyLogTimeline";
import { CheckInMethodSheet } from "../../checkin/components/CheckInMethodSheet";

// 지도 위 이름표의 인증 횟수 색. 채우면 초록, 아니면 보라.
const COUNT_DONE = "#16A300";
const COUNT_ONGOING = "#774AD3";
import { RecentCheckInLog } from "../../checkin/components/RecentCheckInLog";
import { MergeArchiveSection } from "../../record/components/MergeArchiveSection";

export function ChallengeDashboard({
  data,
  description,
  mapType,
  members,
  characters,
  isCurrent,
  currentKnown,
  currentUserId,
  onExtensionSaved,
}: {
  data: ChallengeStatusResponse;
  description?: string | null;
  mapType?: MapType;
  members: MemberTodayStatusResponse[] | null;
  characters: ChallengeCharacterResponse[] | null;
  isCurrent: boolean;
  currentKnown: boolean;
  currentUserId?: number;
  onExtensionSaved: () => void;
}) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [tab, setTab] = useState("현황");
  const [sheetOpen, setSheetOpen] = useState(false);
  const { challenge } = data;
  const canCheckIn =
    isCurrent &&
    challenge.status === "ACTIVE" &&
    data.isCheckInDay &&
    !data.myCompleted;
  const checkInHint = canCheckIn
    ? null
    : challenge.status === "READY"
      ? "시작 전인 시즌은 인증할 수 없어요."
      : challenge.status === "ENDED"
        ? "종료된 시즌은 기록 조회만 가능해요."
        : !isCurrent
          ? currentKnown
            ? "현재 시즌에서만 인증할 수 있어요."
            : "현재 시즌 정보를 확인할 수 없어 인증할 수 없어요."
          : data.isCheckInDay
            ? null
            : "오늘은 인증하는 날이 아니에요.";
  const gym = mapType === "GYM";
  return (
    <>
      <section className="space-y-3 rounded-2xl border border-purple-300 p-4">
        <div className="flex items-start justify-between gap-3">
          <p className="min-w-0 flex-1 text-[17px] leading-snug font-bold text-gray-900 wrap-anywhere line-clamp-2">
            {description}
          </p>
          <div className="flex shrink-0 items-center gap-1">
            <StatusBadge status={challenge.status} />
            <button
              type="button"
              aria-label="채팅"
              onClick={() => showToast("채팅 기능은 준비중입니다.")}
              className="rounded-lg p-1 text-purple-500 hover:bg-purple-50 hover:text-purple-700"
            >
              <ChatIcon className="h-5 w-5" />
            </button>
          </div>
        </div>
        <p className="flex items-center gap-2 text-xs text-gray-500">
          <img src={people} alt="" className="h-4 w-4 object-contain" />
          {data.participantCount}명
          <span aria-hidden className="h-3 w-px shrink-0 bg-gray-200" />
          <span className="min-w-0 text-purple-700 wrap-anywhere">
            {frequencyLabel(challenge)} · 하루 {challenge.dailyCheckInCount}회
            인증
          </span>
        </p>
        <ChallengeProgress
          {...data}
          groupCompletedDayCount={challenge.groupCompletedDayCount}
          remainingDays={Math.max(0, data.totalDays - data.currentDay)}
        />
        <button
          type="button"
          disabled={!canCheckIn}
          onClick={() => setSheetOpen(true)}
          aria-describedby={checkInHint ? "check-in-unavailable" : undefined}
          className="flex h-12 w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-purple-100 font-semibold text-purple-700 hover:bg-purple-200 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-500 disabled:opacity-50"
        >
          <img src={camera} alt="" className="h-5 w-5 object-contain" />
          오늘 인증하기
          {challenge.status === "ACTIVE" && data.isCheckInDay && (
            <span className="rounded-md bg-white/70 px-2 py-0.5 text-xs tabular-nums">
              {data.myCurrentCount} / {challenge.dailyCheckInCount}
            </span>
          )}
        </button>
        {checkInHint && (
          <p
            id="check-in-unavailable"
            className="text-center text-xs text-gray-500"
          >
            {checkInHint}
          </p>
        )}
      </section>
      <div
        role="tablist"
        aria-label="챌린지 대시보드"
        className="flex border-b border-purple-200"
      >
        {["현황", "일일 로그", "갤러리"].map((value) => (
          <button
            key={value}
            type="button"
            role="tab"
            id={`challenge-tab-${value}`}
            aria-selected={tab === value}
            aria-controls="challenge-panel"
            onClick={() => setTab(value)}
            className={`flex-1 border-b-2 py-3 text-sm font-semibold ${tab === value ? "border-purple-500 text-purple-700" : "border-transparent text-gray-500"}`}
          >
            {value}
          </button>
        ))}
      </div>
      <section
        role="tabpanel"
        id="challenge-panel"
        aria-labelledby={`challenge-tab-${tab}`}
      >
        {tab === "현황" ? (
          <div className="space-y-4">
            <div className="overflow-hidden rounded-2xl border border-purple-200 bg-purple-50">
              {mapType ? (
                <div className="relative isolate">
                  <img
                    src={gym ? sportsMap : studyMap}
                    alt={gym ? "운동 챌린지 공간" : "공부 챌린지 공간"}
                    className="aspect-[4/3] w-full object-cover"
                  />
                  <div className="absolute bottom-5 left-1/2 flex w-[min(16rem,calc(100%-2rem))] -translate-x-1/2 flex-wrap items-end justify-center gap-x-2 gap-y-2 min-[400px]:gap-x-3">
                    {characters?.map((character) => {
                      // 오늘 인증 횟수는 멤버 목록에 있다. userId 로 맞춘다.
                      const member = members?.find(
                        (m) => m.userId === character.userId,
                      );
                      const showCount =
                        !!member &&
                        challenge.status === "ACTIVE" &&
                        data.isCheckInDay;
                      const done =
                        !!member &&
                        member.todayCheckInCount >= challenge.dailyCheckInCount;

                      return (
                        <div
                          key={character.userId}
                          className="flex w-[calc((100%-1.5rem)/3)] min-w-0 flex-col items-center gap-1"
                        >
                          <CharacterRenderer
                            pose={character.pose}
                            slots={character.slots}
                            label={`${character.nickname} 캐릭터`}
                            className="h-12 w-12 min-[360px]:h-13 min-[360px]:w-13 min-[400px]:h-14 min-[400px]:w-14 sm:h-16 sm:w-16"
                          />
                          <span className="flex max-w-full items-center rounded bg-white/90 text-[10px]">
                            <span className="min-w-0 truncate px-1.5 py-0.5 text-gray-900">
                              {character.nickname}
                            </span>
                            {showCount && (
                              <>
                                <span
                                  className="h-2.5 w-px shrink-0 bg-gray-300"
                                  aria-hidden
                                />
                                <span
                                  className="shrink-0 px-1.5 py-0.5 font-semibold tabular-nums"
                                  style={{
                                    color: done ? COUNT_DONE : COUNT_ONGOING,
                                  }}
                                >
                                  {member.todayCheckInCount}/
                                  {challenge.dailyCheckInCount}
                                </span>
                              </>
                            )}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ) : (
                <p className="p-8 text-center text-sm text-gray-500">
                  챌린지 공간 정보를 확인할 수 없어요.
                </p>
              )}
            </div>
            {isCurrent && challenge.status === "ACTIVE" && (
              <ExtensionChoicePanel
                challengeId={challenge.id}
                endDate={challenge.endDate}
                members={members ?? undefined}
                currentUserId={currentUserId}
                onSaved={onExtensionSaved}
              />
            )}

            <RecentCheckInLog
              challengeId={challenge.id}
              onSeeAll={() => setTab("갤러리")}
            />

            <MergeArchiveSection challengeId={challenge.id} />
          </div>
        ) : tab === "일일 로그" ? (
          <DailyLogTimeline challengeId={challenge.id} />
        ) : (
          <CheckInGalleryTab challengeId={challenge.id} members={members} />
        )}
      </section>
      <CheckInMethodSheet
        isOpen={sheetOpen}
        onClose={() => setSheetOpen(false)}
        loading={false}
        status={{
          currentCount: data.myCurrentCount,
          targetCount: challenge.dailyCheckInCount,
          allowedTypes: challenge.allowedTypes.includes("PHOTO")
            ? ["PHOTO"]
            : [],
        }}
        onSelectPhoto={() => navigate(`/challenges/${challenge.id}/check-in`)}
      />
    </>
  );
}

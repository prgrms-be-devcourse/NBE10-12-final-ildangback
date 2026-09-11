import { CharacterRenderer } from "../../user/components/CharacterRenderer";
import { useNavigate } from "react-router";
import { frequencyLabel } from "../presentation";
import { useState } from "react";
import studyMap from "../../../assets/icons/studyMap.webp";
import sportsMap from "../../../assets/icons/sportsMap.webp";
import people from "../../../assets/icons/people.webp";
import calendar from "../../../assets/icons/griddy_icons_calendar_purple.webp";
import camera from "../../../assets/icons/iconoir_camera.webp";
import check from "../../../assets/icons/ei_check.webp";
import type { MapType } from "../../group/types";
import type {
  ChallengeStatusResponse,
  ChallengeCharacterResponse,
  MemberTodayStatusResponse,
} from "../types";
import { ChallengeProgress, StatusBadge } from "./ChallengeInfo";
import { ExtensionChoicePanel } from "./ExtensionChoicePanel";
import { CheckInGalleryTab } from "../../checkin/components/CheckInGalleryTab";
import { CheckInMethodSheet } from "../../checkin/components/CheckInMethodSheet";

export function ChallengeDashboard({
  data,
  name,
  mapType,
  members,
  characters,
  isCurrent,
  currentKnown,
  currentUserId,
  onDelegate,
  onExtensionSaved,
}: {
  data: ChallengeStatusResponse;
  name: string;
  mapType?: MapType;
  members: MemberTodayStatusResponse[] | null;
  characters: ChallengeCharacterResponse[] | null;
  isCurrent: boolean;
  currentKnown: boolean;
  currentUserId?: number;
  onDelegate?: (member: MemberTodayStatusResponse) => void;
  onExtensionSaved: () => void;
}) {
  const navigate = useNavigate();
  const [tab, setTab] = useState("현황");
  const [delegating, setDelegating] = useState(false);
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
            ? "오늘 인증을 모두 마쳤어요."
            : "오늘은 인증하는 날이 아니에요.";
  const gym = mapType === "GYM";
  return (
    <>
      <section className="space-y-4 rounded-2xl border border-purple-300 p-5">
        <div className="flex items-start justify-between gap-3">
          <h1 className="min-w-0 text-xl font-bold wrap-anywhere">{name}</h1>
          <StatusBadge status={challenge.status} />
        </div>
        <p className="flex items-center gap-2 text-xs text-gray-500">
          <img src={calendar} alt="" className="h-4 w-4 object-contain" />
          {challenge.startDate} ~ {challenge.endDate}
        </p>
        <p className="flex items-center gap-2 text-xs text-gray-500">
          <img src={people} alt="" className="h-4 w-4 object-contain" />
          {data.participantCount}명 참여
        </p>
        <p className="text-xs text-purple-700">
          {frequencyLabel(challenge)} · 하루 {challenge.dailyCheckInCount}회
          인증
        </p>
        <ChallengeProgress
          {...data}
          groupCompletedDayCount={challenge.groupCompletedDayCount}
        />
        <p className="text-right text-xs text-purple-500">
          남은 인증 예정일 {Math.max(0, data.totalDays - data.currentDay)}일
        </p>
        {challenge.status === "ACTIVE" && data.isCheckInDay && (
          <p className="text-sm text-purple-700">
            오늘 내 인증 {data.myCurrentCount} / {challenge.dailyCheckInCount}
            {data.myCompleted && " · 완료"}
          </p>
        )}
        <button
          type="button"
          disabled={!canCheckIn}
          onClick={() => setSheetOpen(true)}
          aria-describedby={checkInHint ? "check-in-unavailable" : undefined}
          className="flex h-13 w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-purple-100 font-semibold text-purple-700 hover:bg-purple-200 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-500 disabled:opacity-50"
        >
          <img src={camera} alt="" className="h-5 w-5 object-contain" />
          오늘 인증하기
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
                    {characters?.map((character) => (
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
                        <span className="max-w-full truncate rounded bg-white/90 px-2 py-1 text-[10px] text-gray-900">
                          {character.nickname}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <p className="p-8 text-center text-sm text-gray-500">
                  챌린지 공간 정보를 확인할 수 없어요.
                </p>
              )}
              {members && (
                <div className="bg-white p-4">
                  {onDelegate && (
                    <div className="mb-3 flex items-center justify-between gap-2">
                      <span className="text-xs text-gray-500">
                        {delegating
                          ? "위임할 그룹원을 선택해주세요."
                          : "그룹원"}
                      </span>
                      <button
                        type="button"
                        onClick={() => setDelegating((value) => !value)}
                        className="text-xs font-semibold text-purple-700 underline"
                      >
                        {delegating ? "위임 취소" : "위임하기"}
                      </button>
                    </div>
                  )}
                  <ul className="grid grid-cols-3 gap-4">
                    {members.map((member) => {
                      const isDelegateTarget =
                        delegating &&
                        !!onDelegate &&
                        member.userId !== challenge.ownerId;
                      return (
                        <li key={member.userId} className="min-w-0 text-center">
                          <div className="flex flex-wrap items-center justify-center gap-x-1">
                            {isDelegateTarget ? (
                              <button
                                type="button"
                                onClick={() => {
                                  onDelegate(member);
                                  setDelegating(false);
                                }}
                                aria-label={`${member.nickname} 님에게 그룹장 위임`}
                                className="min-w-0 max-w-full cursor-pointer truncate rounded px-1 text-xs font-semibold text-purple-700 underline decoration-dotted hover:text-purple-900"
                              >
                                {member.nickname}
                              </button>
                            ) : (
                              <span className="min-w-0 truncate text-xs font-semibold">
                                {member.nickname}
                              </span>
                            )}
                          </div>
                          {challenge.status === "ACTIVE" &&
                            data.isCheckInDay && (
                              <p className="mt-1 flex items-center justify-center gap-1 text-xs text-purple-500">
                                {member.todayCheckInCount}/
                                {challenge.dailyCheckInCount}
                                {member.todayCheckInCount >=
                                  challenge.dailyCheckInCount && (
                                  <img
                                    src={check}
                                    alt="인증 완료"
                                    className="h-4 w-4 object-contain"
                                  />
                                )}
                              </p>
                            )}
                        </li>
                      );
                    })}
                  </ul>
                </div>
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
          </div>
        ) : tab === "갤러리" ? (
          <CheckInGalleryTab challengeId={challenge.id} members={members} />
        ) : (
          <p className="rounded-2xl bg-purple-50 px-5 py-12 text-center text-sm text-gray-500">
            {tab} 기능을 준비 중이에요.
          </p>
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

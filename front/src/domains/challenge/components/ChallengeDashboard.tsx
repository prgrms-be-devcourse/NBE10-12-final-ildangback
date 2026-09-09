import { useToast } from "../../../shared/lib/useToast";
import { frequencyLabel } from "../presentation";
import { useState } from "react";
import studyMap from "../../../assets/icons/studyMap.webp";
import sportsMap from "../../../assets/icons/sportsMap.webp";
import studyChar from "../../../assets/icons/studyChar.webp";
import sportChar from "../../../assets/icons/sportChar.webp";
import people from "../../../assets/icons/people.webp";
import calendar from "../../../assets/icons/griddy_icons_calendar_purple.webp";
import camera from "../../../assets/icons/iconoir_camera.webp";
import check from "../../../assets/icons/ei_check.webp";
import type { MapType } from "../../group/types";
import type {
  ChallengeStatusResponse,
  MemberTodayStatusResponse,
} from "../types";
import { ChallengeProgress, StatusBadge } from "./ChallengeInfo";

export function ChallengeDashboard({
  data,
  name,
  mapType,
  members,
  isCurrent,
  currentKnown,
}: {
  data: ChallengeStatusResponse;
  name: string;
  mapType?: MapType;
  members: MemberTodayStatusResponse[] | null;
  isCurrent: boolean;
  currentKnown: boolean;
}) {
  const { showToast } = useToast();
  const [tab, setTab] = useState("현황");
  const { challenge } = data;
  const canCheckIn = isCurrent && challenge.status === "ACTIVE";
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
        <ChallengeProgress {...data} />
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
          onClick={() => showToast("인증 기능은 준비중입니다.")}
          aria-describedby="check-in-unavailable"
          className="flex h-13 w-full cursor-pointer items-center justify-center gap-2 rounded-xl bg-purple-100 font-semibold text-purple-700 hover:bg-purple-200 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-500 disabled:opacity-50"
        >
          <img src={camera} alt="" className="h-5 w-5 object-contain" />
          오늘 인증하기
        </button>
        <p
          id="check-in-unavailable"
          className="text-center text-xs text-gray-500"
        >
          {canCheckIn
            ? "인증 기능은 준비중입니다."
            : challenge.status === "READY"
              ? "시작 전인 시즌은 인증할 수 없어요."
              : challenge.status === "ENDED"
                ? "종료된 시즌은 기록 조회만 가능해요."
                : currentKnown
                  ? "현재 시즌에서만 인증할 수 있어요."
                  : "현재 시즌 정보를 확인할 수 없어 인증할 수 없어요."}
        </p>
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
          <div className="overflow-hidden rounded-2xl border border-purple-200 bg-purple-50">
            {mapType ? (
              <div className="relative isolate">
                <img
                  src={gym ? sportsMap : studyMap}
                  alt={gym ? "운동 챌린지 공간" : "공부 챌린지 공간"}
                  className="aspect-[4/3] w-full object-cover"
                />
                <div className="absolute inset-x-4 bottom-5 flex flex-wrap items-end justify-center gap-x-5 gap-y-2">
                  {members?.map((member) => (
                    <div
                      key={member.userId}
                      className="flex flex-col items-center gap-1"
                    >
                      <img
                        src={gym ? sportChar : studyChar}
                        alt=""
                        className="h-10 w-10 object-contain [image-rendering:pixelated]"
                      />
                      <span className="max-w-20 truncate rounded bg-white/90 px-2 py-1 text-[10px] text-gray-900">
                        {member.nickname}
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
              <ul className="grid grid-cols-3 gap-4 bg-white p-4">
                {members.map((member) => (
                  <li key={member.userId} className="min-w-0 text-center">
                    <p className="truncate text-xs font-semibold">
                      {member.nickname}
                    </p>
                    {challenge.status === "ACTIVE" && data.isCheckInDay && (
                      <p className="mt-1 flex items-center justify-center gap-1 text-xs text-purple-500">
                        {member.todayCheckInCount}/{challenge.dailyCheckInCount}
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
                ))}
              </ul>
            )}
          </div>
        ) : (
          <p className="rounded-2xl bg-purple-50 px-5 py-12 text-center text-sm text-gray-500">
            {tab} 기능을 준비 중이에요.
          </p>
        )}
      </section>
    </>
  );
}

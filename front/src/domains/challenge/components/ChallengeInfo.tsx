import calendarIcon from "../../../assets/icons/griddy_icons_calendar_purple.webp";
import cycleIcon from "../../../assets/icons/material_symbols_cycle_rounded.webp";
import checkboxIcon from "../../../assets/icons/griddy_icons_checkbox.webp";
import cameraIcon from "../../../assets/icons/iconoir_camera.webp";
import { frequencyLabel } from "../presentation";
import type { ReactNode } from "react";
import type { ChallengeSettings, ChallengeStatus } from "../types";

export function StatusBadge({ status }: { status: ChallengeStatus }) {
  return (
    <span
      className={`shrink-0 rounded-md px-2 py-1 text-[12px] font-semibold ${status === "ENDED" ? "bg-gray-100 text-gray-500" : "bg-purple-50 text-purple-500"}`}
    >
      {{ READY: "시작 전", ACTIVE: "진행 중", ENDED: "종료" }[status]}
    </span>
  );
}
export function InfoRow({
  label,
  icon,
  children,
}: {
  label: string;
  icon?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-start justify-between gap-4 py-3 text-[13px]">
      <dt className="flex shrink-0 items-center gap-2 text-gray-500">
        {icon && (
          <img
            src={icon}
            alt=""
            width={18}
            height={18}
            className="h-[18px] w-[18px] shrink-0 object-contain"
          />
        )}
        {label}
      </dt>
      <dd className="min-w-0 text-right wrap-anywhere whitespace-pre-wrap">
        {children}
      </dd>
    </div>
  );
}
export function ChallengeRulesCard({
  settings,
}: {
  settings: ChallengeSettings;
}) {
  return (
    <section className="rounded-2xl border border-purple-200 p-4">
      <h2 className="text-[17px] font-bold">챌린지 규칙</h2>
      <dl className="mt-2 divide-y divide-purple-100">
        <InfoRow label="진행 기간" icon={calendarIcon}>
          {settings.startDate} ~ {settings.endDate}
        </InfoRow>
        <InfoRow label="인증 빈도" icon={cycleIcon}>
          {frequencyLabel(settings)}
        </InfoRow>
        <InfoRow label="하루 인증" icon={checkboxIcon}>
          {settings.dailyCheckInCount}회
        </InfoRow>
        <InfoRow label="인증 방식" icon={cameraIcon}>
          {settings.allowedTypes.includes("PHOTO")
            ? "사진"
            : "지원되는 인증 방식 없음"}
        </InfoRow>
        <InfoRow label="인증 가능 시간">04:00 ~ 익일 03:59 (KST)</InfoRow>
      </dl>
      <p className="mt-2 text-[11px] text-purple-500">
        인증 규칙은 시작 후 변경할 수 없어요.
      </p>
    </section>
  );
}
export function ChallengeProgress({
  currentDay,
  totalDays,
  periodProgressRate,
  groupCompletedDayCount,
  variant = "group",
}: {
  currentDay: number;
  totalDays: number;
  periodProgressRate: number;
  groupCompletedDayCount?: number;
  variant?: "group" | "period";
}) {
  // 기간 전용 표시는 그룹 목록에서 명시적으로 요청한다. 성공일 누락을 기간 진행률로 대체하지 않는다.
  const showsGroupProgress = variant === "group";
  const hasSuccessCount =
    typeof groupCompletedDayCount === "number" &&
    Number.isFinite(groupCompletedDayCount) &&
    groupCompletedDayCount >= 0;
  const successRate = !hasSuccessCount
    ? null
    : totalDays > 0
      ? Math.round((groupCompletedDayCount / totalDays) * 1000) / 10
      : 0;
  const displayedRate = showsGroupProgress ? successRate : periodProgressRate;
  return (
    <div className="space-y-3">
      <p className="text-[14px]">
        Day <strong>{currentDay}</strong> / {totalDays}{" "}
        <span className="text-[11px] text-gray-500">인증 예정일 기준</span>
      </p>
      <div className="flex items-center gap-2">
        <div
          role="progressbar"
          aria-label={showsGroupProgress ? "그룹 인증 성공률" : "챌린지 진행률"}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={displayedRate ?? undefined}
          aria-valuetext={
            displayedRate === null ? "그룹 성공일 정보 없음" : undefined
          }
          className="relative h-2.5 flex-1 overflow-hidden rounded-full bg-gray-100"
        >
          {showsGroupProgress && (
            <div
              className="absolute inset-y-0 left-0 rounded-full bg-gray-400"
              style={{
                width: `${Math.max(0, Math.min(100, periodProgressRate))}%`,
              }}
            />
          )}
          <div
            className="absolute inset-y-0 left-0 z-10 rounded-full bg-purple-500"
            style={{
              width: `${Math.max(0, Math.min(100, displayedRate ?? 0))}%`,
            }}
          />
        </div>
        <span
          className="text-[13px] font-semibold text-purple-500"
          title={
            displayedRate === null
              ? "그룹 성공일 정보가 응답에 없습니다."
              : undefined
          }
        >
          {displayedRate === null ? "—" : `${displayedRate}%`}
        </span>
      </div>
    </div>
  );
}

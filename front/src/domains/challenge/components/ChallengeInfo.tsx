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
}: {
  currentDay: number;
  totalDays: number;
  periodProgressRate: number;
}) {
  return (
    <div className="space-y-3">
      <p className="text-[14px]">
        Day <strong>{currentDay}</strong> / {totalDays}{" "}
        <span className="text-[11px] text-gray-500">인증 예정일 기준</span>
      </p>
      <div className="flex items-center gap-2">
        <div
          role="progressbar"
          aria-label="챌린지 진행률"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={periodProgressRate}
          className="h-2.5 flex-1 overflow-hidden rounded-full bg-gray-100"
        >
          <div
            className="h-full rounded-full bg-purple-500"
            style={{
              width: `${Math.max(0, Math.min(100, periodProgressRate))}%`,
            }}
          />
        </div>
        <span className="text-[13px] font-semibold text-purple-500">
          {periodProgressRate}%
        </span>
      </div>
    </div>
  );
}

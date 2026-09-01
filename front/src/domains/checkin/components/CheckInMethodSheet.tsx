import { CameraIcon, VideoCameraIcon } from "@phosphor-icons/react";
import type { ReactNode } from "react";
import { BottomSheet } from "../../../shared/ui/BottomSheet";
import type { TodayCheckInStatus } from "../types";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  loading: boolean;
  error?: boolean;
  status: TodayCheckInStatus | null;
  onSelectPhoto: () => void;
}

/**
 * 와이어프레임 1번 — "오늘 인증하기" 방법 선택 시트.
 * 라이브는 이번 스코프에서 제외, 영상은 비활성(추후 구현).
 */
export function CheckInMethodSheet({
  isOpen,
  onClose,
  loading,
  error = false,
  status,
  onSelectPhoto,
}: Props) {
  const photoAllowed = status?.allowedTypes.includes("PHOTO") ?? false;

  return (
    <BottomSheet isOpen={isOpen} onClose={onClose} title="오늘 인증하기">
      {loading ? (
        <div className="flex justify-center py-10">
          <span
            role="status"
            aria-label="불러오는 중"
            className="size-7 animate-spin rounded-full border-3 border-purple-200 border-t-purple-500"
          />
        </div>
      ) : error || !status ? (
        <p className="py-10 text-center text-[14px] text-gray-500">
          지금은 인증 상태를 불러올 수 없어요. 잠시 후 다시 시도해 주세요.
        </p>
      ) : (
        <>
          <p className="text-center text-[17px] font-semibold">
            <span className="text-purple-500">{status.currentCount}</span>
            <span className="text-gray-400"> / {status.targetCount}회</span>
          </p>
          <p className="mt-1 mb-5 text-center text-[13px] text-gray-400">
            인증 방법을 선택해 주세요
          </p>

          <div className="flex flex-col gap-3">
            <MethodRow
              icon={<CameraIcon size={26} weight="fill" />}
              title="사진 인증"
              subtitle="멘트 추천 받아요"
              disabled={!photoAllowed}
              onClick={onSelectPhoto}
            />
            <MethodRow
              icon={<VideoCameraIcon size={26} weight="fill" />}
              title="영상 인증"
              subtitle="추후 구현 예정이에요"
              disabled
            />
          </div>
        </>
      )}
    </BottomSheet>
  );
}

function MethodRow({
  icon,
  title,
  subtitle,
  disabled = false,
  onClick,
}: {
  icon: ReactNode;
  title: string;
  subtitle: string;
  disabled?: boolean;
  onClick?: () => void;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="flex w-full items-center gap-4 rounded-2xl bg-purple-50 px-5 py-4 text-left transition-colors enabled:hover:bg-purple-100 disabled:opacity-45"
    >
      <span className="text-purple-500">{icon}</span>
      <span>
        <span className="block text-[16px] font-bold text-gray-900">
          {title}
        </span>
        <span className="block text-[13px] text-gray-500">{subtitle}</span>
      </span>
    </button>
  );
}

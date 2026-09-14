import { useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { BottomSheet } from "../../../shared/ui/BottomSheet";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { submitReport } from "../api";
import type { ReportReason, ReportTargetType } from "../types";
import { REASON_LABEL } from "../types";

const REASONS: ReportReason[] = ["ABUSE", "SPAM", "SEXUAL", "FAKE", "ETC"];

const DETAIL_MAX = 500;

export interface ReportTarget {
  targetType: ReportTargetType;
  targetId: number;
  /** "이 인증", "닉네임 꼬밋러" 처럼 무엇을 신고하는지 한 줄로. */
  label: string;
}

interface Props {
  isOpen: boolean;
  onClose: () => void;
  /** 한 화면에서 고를 수 있는 대상들. 하나면 선택 단계를 건너뛴다. */
  targets: ReportTarget[];
  onSubmitted?: () => void;
}

/**
 * 신고 시트. 대상 종류와 관계없이 한 벌이다 — 서버가 대상에서 제재받을 사람을
 * 스스로 찾으므로 화면은 "무엇을 신고하는지"만 넘기면 된다.
 */
export function ReportDialog({ isOpen, onClose, targets, onSubmitted }: Props) {
  const [targetIndex, setTargetIndex] = useState(0);
  const [reason, setReason] = useState<ReportReason>("ABUSE");
  const [detail, setDetail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 닫을 때 비운다. 여는 쪽(effect)에서 하면 cascading render 가 되고 린트가 막는다.
  function close() {
    setTargetIndex(0);
    setReason("ABUSE");
    setDetail("");
    setError(null);
    onClose();
  }

  const target = targets[targetIndex];
  const detailRequired = reason === "ETC";

  function handleSubmit() {
    if (!target) return;
    if (detailRequired && detail.trim() === "") {
      setError("기타 사유는 상황 설명을 입력해 주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    submitReport({
      targetType: target.targetType,
      targetId: target.targetId,
      reason,
      detail: detail.trim() === "" ? null : detail.trim(),
    })
      .then(() => {
        onSubmitted?.();
        close();
      })
      .catch((e) => setError(reportErrorMessage(e)))
      .finally(() => setSaving(false));
  }

  return (
    <BottomSheet isOpen={isOpen} onClose={close} title="신고">
      {targets.length > 1 ? (
        <fieldset className="mb-4">
          <legend className="mb-2 text-[14px] font-semibold text-gray-900">
            무엇을 신고하나요
          </legend>
          <div className="flex gap-2">
            {targets.map((candidate, index) => (
              <button
                key={`${candidate.targetType}-${candidate.targetId}`}
                type="button"
                onClick={() => setTargetIndex(index)}
                className={`rounded-full px-3 py-1.5 text-[13px] font-semibold ${
                  targetIndex === index
                    ? "bg-purple-500 text-white"
                    : "bg-gray-100 text-gray-600"
                }`}
              >
                {candidate.label}
              </button>
            ))}
          </div>
        </fieldset>
      ) : (
        <p className="mb-4 text-[13px] text-gray-500">{target?.label}</p>
      )}

      <fieldset className="mb-4">
        <legend className="mb-2 text-[14px] font-semibold text-gray-900">
          신고 사유
        </legend>
        <div className="space-y-2">
          {REASONS.map((value) => (
            <label
              key={value}
              className={`flex items-center gap-3 rounded-xl border px-4 py-3 text-[14px] ${
                reason === value
                  ? "border-purple-400 bg-purple-50 text-gray-900"
                  : "border-gray-200 text-gray-600"
              }`}
            >
              <input
                type="radio"
                name="report-reason"
                value={value}
                checked={reason === value}
                onChange={() => setReason(value)}
                className="accent-purple-500"
              />
              {REASON_LABEL[value]}
            </label>
          ))}
        </div>
      </fieldset>

      <label
        htmlFor="report-detail"
        className="mb-2 block text-[14px] font-semibold text-gray-900"
      >
        상황 설명{detailRequired ? "" : " (선택)"}
      </label>
      <textarea
        id="report-detail"
        value={detail}
        maxLength={DETAIL_MAX}
        onChange={(e) => setDetail(e.target.value)}
        rows={4}
        placeholder={
          detailRequired
            ? "어떤 점이 문제인지 적어 주세요"
            : "사유만으로 전달되지 않는 맥락이 있으면 적어 주세요"
        }
        className="w-full rounded-xl border border-gray-200 px-4 py-3 text-[14px] focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
      />
      <p className="mt-1 mb-4 text-right text-[12px] text-gray-400">
        {detail.length}/{DETAIL_MAX}
      </p>

      <FormAlert message={error} />

      <Button
        type="button"
        onClick={handleSubmit}
        loading={saving}
        className="mt-4"
      >
        신고하기
      </Button>
    </BottomSheet>
  );
}

/** 서버 code 로 분기한다. message 를 그대로 띄우지 않는 것이 이 저장소 규약이다. */
function reportErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  switch (error.code) {
    case "DUPLICATE_PENDING_REPORT":
      return "이미 신고하셨어요. 처리가 끝나면 다시 신고할 수 있어요.";
    case "SELF_REPORT_NOT_ALLOWED":
      return "자기 자신은 신고할 수 없어요.";
    case "REPORT_DETAIL_REQUIRED":
      return "기타 사유는 상황 설명을 입력해 주세요.";
    case "REPORT_TARGET_NOT_SUPPORTED":
      return "아직 신고할 수 없는 대상이에요.";
    case "USER_NOT_FOUND":
    case "CHECK_IN_NOT_FOUND":
      return "신고 대상을 찾을 수 없어요. 이미 삭제됐을 수 있어요.";
    default:
      return error.message;
  }
}

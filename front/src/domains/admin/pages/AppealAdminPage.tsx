import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { formatDateTimeMinute } from "../../../shared/lib/date";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import { decideAppeal, getAllAppeals } from "../../report/api";
import type { AppealDetailResponse, AppealStatus } from "../../report/types";
import {
  APPEAL_STATUS_LABEL,
  PENALTY_LABEL,
  REASON_LABEL,
  TARGET_LABEL,
} from "../../report/types";

const FILTERS: { value: AppealStatus | null; label: string }[] = [
  { value: "PENDING", label: "대기" },
  { value: "ACCEPTED", label: "인용" },
  { value: "REJECTED", label: "기각" },
  { value: null, label: "전체" },
];

/**
 * 관리자 > 이의제기. 신고와 화면을 나눈 이유는 처리 흐름이 엉키지 않게 하기 위해서다.
 *
 * 인용하면 그 신고에 달린 제재가 **전부** 해제된다. 삭제된 인증 콘텐츠는 복구되지 않는다.
 */
export function AppealAdminPage() {
  const [status, setStatus] = useState<AppealStatus | null>("PENDING");
  const [appeals, setAppeals] = useState<AppealDetailResponse[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  const reload = useCallback(() => {
    getAllAppeals(status)
      .then((next) => {
        setAppeals(next);
        setListError(null);
      })
      .catch((e) => {
        setAppeals([]);
        setListError(errorMessage(e));
      });
  }, [status]);

  useEffect(reload, [reload]);

  return (
    <>
      <TopBar title="이의제기 관리" />

      <div className="flex-1 px-5 pb-10">
        <div className="mt-2 flex gap-2">
          {FILTERS.map(({ value, label }) => (
            <button
              key={label}
              type="button"
              onClick={() => setStatus(value)}
              className={`rounded-full px-3 py-1.5 text-[13px] font-semibold ${
                status === value
                  ? "bg-purple-500 text-white"
                  : "bg-gray-100 text-gray-600"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <h2 className="mt-4 mb-3 text-[15px] font-bold text-gray-900">
          이의제기{appeals ? ` ${appeals.length}건` : ""}
        </h2>

        <FormAlert message={listError} />

        {appeals === null ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            불러오는 중…
          </p>
        ) : appeals.length === 0 ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            해당하는 이의제기가 없어요
          </p>
        ) : (
          <ul className="space-y-3">
            {appeals.map((appeal) => (
              <AppealCard key={appeal.id} appeal={appeal} onDecided={reload} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function AppealCard({
  appeal,
  onDecided,
}: {
  appeal: AppealDetailResponse;
  onDecided: () => void;
}) {
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const report = appeal.report;

  function run(accept: boolean) {
    setSaving(true);
    setError(null);
    decideAppeal(appeal.id, { accept })
      .then(onDecided)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setSaving(false));
  }

  return (
    <li className="rounded-2xl border border-gray-200 p-4">
      <div className="flex items-start gap-2">
        <span className="rounded-md bg-gray-100 px-2 py-0.5 text-[12px] font-semibold text-gray-600">
          신고 #{appeal.reportId}
        </span>
        <span className="ml-auto text-[12px] text-gray-400">
          {APPEAL_STATUS_LABEL[appeal.status]}
        </span>
      </div>

      <p className="mt-3 rounded-xl bg-gray-50 px-3 py-2 text-[13px] text-gray-700">
        “{appeal.content}”
      </p>
      <p className="mt-1 text-[12px] text-gray-400">
        #{appeal.appellantId} · {formatDateTimeMinute(appeal.createdAt)}
      </p>

      {report && (
        <div className="mt-3 border-t border-gray-100 pt-3">
          <p className="text-[13px] font-semibold text-gray-900">원 신고</p>
          <p className="mt-1 text-[13px] text-gray-600">
            {TARGET_LABEL[report.targetType]} · {REASON_LABEL[report.reason]} ·
            과거 제재 {report.pastPenaltyCount}건
          </p>
          {report.reportedContent && (
            <p className="mt-2 rounded-xl bg-gray-50 px-3 py-2 text-[13px] break-all text-gray-700">
              {report.reportedContent}
            </p>
          )}
          {report.detail && (
            <p className="mt-1 text-[13px] text-gray-500">“{report.detail}”</p>
          )}
          {report.penalties.length > 0 && (
            <ul className="mt-2 space-y-1 text-[13px] text-gray-600">
              {report.penalties.map((penalty) => (
                <li key={penalty.id}>
                  {PENALTY_LABEL[penalty.penaltyType]}
                  {penalty.endsAt
                    ? ` · ${formatDateTimeMinute(penalty.endsAt)}까지`
                    : ""}
                  {penalty.amount != null ? ` · ${penalty.amount}P` : ""}
                  {penalty.revokedAt ? " · 해제됨" : ""}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}

      <FormAlert message={error} />

      {appeal.status === "PENDING" ? (
        <div className="mt-4 flex gap-2">
          <Button type="button" onClick={() => run(true)} loading={saving}>
            인용 (제재 해제)
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={() => run(false)}
            loading={saving}
          >
            기각
          </Button>
        </div>
      ) : (
        <p className="mt-3 text-[12px] text-gray-400">
          {appeal.decidedAt
            ? `${formatDateTimeMinute(appeal.decidedAt)} 판정`
            : "판정 완료"}
        </p>
      )}
    </li>
  );
}

function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  switch (error.code) {
    case "APPEAL_ALREADY_DECIDED":
      return "이미 판정이 끝난 이의제기예요. 목록을 새로고침해 주세요.";
    case "APPEAL_NOT_FOUND":
      return "그 이의제기를 찾을 수 없어요.";
    default:
      return error.message;
  }
}

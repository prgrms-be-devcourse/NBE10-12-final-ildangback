import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { formatDateTimeMinute } from "../../../shared/lib/date";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import { getAllMyPenalties, submitAppeal } from "../api";
import type { MyPenaltyResponse, PenaltyResponse } from "../types";
import { APPEAL_STATUS_LABEL, PENALTY_LABEL, REASON_LABEL } from "../types";

const CONTENT_MAX = 1000;

/**
 * 프로필 > 내 제재. 받은 제재를 보고 이의제기를 낸다.
 *
 * 이의제기는 신고 1건에 한 번이고, 인용되면 그 건에 달린 제재가 전부 풀린다.
 * 정지 중에는 로그인이 막히므로 이 화면은 제재 직후 남은 AT 로만 들어올 수 있다.
 */
export function MyPenaltiesPage() {
  const [items, setItems] = useState<MyPenaltyResponse[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  const reload = useCallback(() => {
    getAllMyPenalties()
      .then((next) => {
        setItems(next);
        setListError(null);
      })
      .catch((e) => {
        setItems([]);
        setListError(errorMessage(e));
      });
  }, []);

  useEffect(reload, [reload]);

  return (
    <>
      <TopBar title="내 제재" />

      <div className="flex-1 px-5 pb-10">
        <p className="mt-2 text-[13px] text-gray-500">
          받은 제재와 이의제기 결과입니다. 이의제기는 건마다 한 번 낼 수 있어요.
        </p>

        <FormAlert message={listError} />

        {items === null ? (
          <p className="py-12 text-center text-[14px] text-gray-400">
            불러오는 중…
          </p>
        ) : items.length === 0 ? (
          <p className="py-12 text-center text-[14px] text-gray-400">
            받은 제재가 없어요
          </p>
        ) : (
          <ul className="mt-4 space-y-3">
            {items.map((item) => (
              <PenaltyCard
                key={item.reportId}
                item={item}
                onAppealed={reload}
              />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function PenaltyCard({
  item,
  onAppealed,
}: {
  item: MyPenaltyResponse;
  onAppealed: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <li className="rounded-2xl border border-gray-200 p-4">
      <div className="flex items-start gap-2">
        <span className="rounded-md bg-purple-50 px-2 py-0.5 text-[12px] font-semibold text-purple-500">
          {REASON_LABEL[item.reason]}
        </span>
        {item.decidedAt && (
          <span className="ml-auto text-[12px] text-gray-400">
            {formatDateTimeMinute(item.decidedAt)}
          </span>
        )}
      </div>

      <ul className="mt-3 space-y-1">
        {item.penalties.map((penalty) => (
          <li key={penalty.id} className="text-[14px] text-gray-800">
            {describe(penalty)}
          </li>
        ))}
      </ul>

      {item.detail && (
        <p className="mt-2 text-[13px] text-gray-500">“{item.detail}”</p>
      )}

      {item.appeal ? (
        <div className="mt-4 rounded-xl bg-gray-50 px-3 py-2.5">
          <p className="text-[13px] font-semibold text-gray-900">
            이의제기 {APPEAL_STATUS_LABEL[item.appeal.status]}
          </p>
          <p className="mt-1 text-[13px] text-gray-600">
            “{item.appeal.content}”
          </p>
          {item.appeal.status === "PENDING" && (
            <p className="mt-1 text-[12px] text-gray-400">
              관리자가 확인하고 있어요
            </p>
          )}
          {item.appeal.status === "ACCEPTED" && (
            <p className="mt-1 text-[12px] text-gray-400">
              제재가 해제됐어요. 삭제된 인증 사진은 되돌아오지 않아요
            </p>
          )}
        </div>
      ) : open ? (
        <AppealForm
          reportId={item.reportId}
          onDone={() => {
            setOpen(false);
            onAppealed();
          }}
          onCancel={() => setOpen(false)}
        />
      ) : (
        item.appealable && (
          <Button
            type="button"
            variant="secondary"
            onClick={() => setOpen(true)}
            className="mt-4"
          >
            이의제기
          </Button>
        )
      )}
    </li>
  );
}

function AppealForm({
  reportId,
  onDone,
  onCancel,
}: {
  reportId: number;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [content, setContent] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function submit() {
    if (content.trim() === "") {
      setError("이의제기 내용을 입력해 주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    submitAppeal(reportId, { content: content.trim() })
      .then(onDone)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setSaving(false));
  }

  return (
    <div className="mt-4 border-t border-gray-100 pt-4">
      <label
        htmlFor={`appeal-${reportId}`}
        className="mb-2 block text-[14px] font-semibold text-gray-900"
      >
        이의제기 내용
      </label>
      <textarea
        id={`appeal-${reportId}`}
        value={content}
        maxLength={CONTENT_MAX}
        onChange={(e) => setContent(e.target.value)}
        rows={5}
        placeholder="어떤 점이 잘못됐는지 적어 주세요. 한 번만 낼 수 있어요."
        className="w-full rounded-xl border border-gray-200 px-4 py-3 text-[14px] focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
      />
      <p className="mt-1 text-right text-[12px] text-gray-400">
        {content.length}/{CONTENT_MAX}
      </p>

      <FormAlert message={error} />

      <div className="mt-3 flex gap-2">
        <Button type="button" onClick={submit} loading={saving}>
          제출
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={onCancel}
          loading={saving}
        >
          취소
        </Button>
      </div>
    </div>
  );
}

/** 제재 한 줄. 종류마다 채워지는 값이 달라서 붙는 설명이 다르다. */
function describe(penalty: PenaltyResponse): string {
  const label = PENALTY_LABEL[penalty.penaltyType];
  const revoked = penalty.revokedAt ? " (해제됨)" : "";

  if (penalty.penaltyType === "SUSPENSION" && penalty.endsAt) {
    return `${label} · ${formatDateTimeMinute(penalty.endsAt)}까지${revoked}`;
  }
  if (penalty.penaltyType === "POINT_FORFEIT" && penalty.amount != null) {
    return `${label} · ${penalty.amount}P${revoked}`;
  }
  return `${label}${revoked}`;
}

function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  switch (error.code) {
    case "APPEAL_ALREADY_EXISTS":
      return "이미 이의제기를 내셨어요. 건마다 한 번만 낼 수 있어요.";
    case "APPEAL_NOT_ALLOWED":
      return "이의제기를 낼 수 없는 건이에요.";
    case "REPORT_NOT_FOUND":
      return "그 제재를 찾을 수 없어요.";
    default:
      return error.message;
  }
}

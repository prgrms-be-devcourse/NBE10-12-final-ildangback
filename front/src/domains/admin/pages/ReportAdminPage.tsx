import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { formatDateTimeMinute } from "../../../shared/lib/date";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TextField } from "../../../shared/ui/TextField";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  decideReport,
  getAllReports,
  penalizeDirectly,
} from "../../report/api";
import type {
  PenaltyCommand,
  PenaltyResponse,
  PenaltyType,
  ReportDetailResponse,
  ReportStatus,
} from "../../report/types";
import {
  PENALTY_LABEL,
  REASON_LABEL,
  STATUS_LABEL,
  TARGET_LABEL,
} from "../../report/types";

const FILTERS: { value: ReportStatus | null; label: string }[] = [
  { value: "PENDING", label: "대기" },
  { value: "ACCEPTED", label: "승인" },
  { value: "REJECTED", label: "기각" },
  { value: null, label: "전체" },
];

const PENALTY_TYPES: PenaltyType[] = [
  "WARNING",
  "SUSPENSION",
  "PERMANENT_BAN",
  "POINT_FORFEIT",
];

/**
 * 운영 기준표. 서버는 권장 제재를 계산하지 않는다 — 관리자가 이 표와
 * pastPenaltyCount 를 보고 직접 고른다.
 */
const GUIDE: {
  reason: string;
  first: string;
  second: string;
  third: string;
}[] = [
  {
    reason: "욕설·도배",
    first: "경고",
    second: "3일 정지",
    third: "30일 정지",
  },
  {
    reason: "선정성",
    first: "7일 정지",
    second: "30일 정지",
    third: "영구 정지",
  },
  {
    reason: "허위 인증",
    first: "경고+압수",
    second: "7일 정지+압수",
    third: "30일 정지+압수",
  },
];

/** 관리자 > 신고. 목록과 판정, 그리고 신고 없이 거는 제재를 한 화면에서 한다. */
export function ReportAdminPage() {
  const [status, setStatus] = useState<ReportStatus | null>("PENDING");
  const [reports, setReports] = useState<ReportDetailResponse[] | null>(null);
  const [listError, setListError] = useState<string | null>(null);

  const reload = useCallback(() => {
    getAllReports(status)
      .then((next) => {
        setReports(next);
        setListError(null);
      })
      .catch((e) => {
        setReports([]);
        setListError(errorMessage(e));
      });
  }, [status]);

  useEffect(reload, [reload]);

  return (
    <>
      <TopBar title="신고 관리" />

      <div className="flex-1 px-5 pb-10">
        <DirectPenaltyForm onDone={reload} />

        <OperatingGuide />

        <div className="mt-8 flex gap-2">
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
          신고{reports ? ` ${reports.length}건` : ""}
        </h2>

        <FormAlert message={listError} />

        {reports === null ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            불러오는 중…
          </p>
        ) : reports.length === 0 ? (
          <p className="py-8 text-center text-[14px] text-gray-400">
            해당하는 신고가 없어요
          </p>
        ) : (
          <ul className="space-y-3">
            {reports.map((report) => (
              <ReportCard key={report.id} report={report} onDecided={reload} />
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

function OperatingGuide() {
  return (
    <details className="mt-6 rounded-2xl border border-gray-200 p-4">
      <summary className="cursor-pointer text-[14px] font-semibold text-gray-900">
        운영 기준표
      </summary>
      <p className="mt-2 text-[12px] text-gray-500">
        차수는 그 사용자의 해제되지 않은 제재 건수 + 1 이며 사유를 가리지
        않는다. 포인트 압수는 허위 인증에만 붙인다.
      </p>
      <table className="mt-3 w-full text-left text-[12px]">
        <thead className="text-gray-400">
          <tr>
            <th className="pb-1 font-medium">사유</th>
            <th className="pb-1 font-medium">1차</th>
            <th className="pb-1 font-medium">2차</th>
            <th className="pb-1 font-medium">3차 이상</th>
          </tr>
        </thead>
        <tbody className="text-gray-600">
          {GUIDE.map((row) => (
            <tr key={row.reason}>
              <td className="py-0.5">{row.reason}</td>
              <td className="py-0.5">{row.first}</td>
              <td className="py-0.5">{row.second}</td>
              <td className="py-0.5">{row.third}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

function ReportCard({
  report,
  onDecided,
}: {
  report: ReportDetailResponse;
  onDecided: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <li className="rounded-2xl border border-gray-200 p-4">
      <div className="flex items-start gap-2">
        <span className="rounded-md bg-gray-100 px-2 py-0.5 text-[12px] font-semibold text-gray-600">
          {TARGET_LABEL[report.targetType]}
        </span>
        <span className="rounded-md bg-purple-50 px-2 py-0.5 text-[12px] font-semibold text-purple-500">
          {REASON_LABEL[report.reason]}
        </span>
        <span className="ml-auto text-[12px] text-gray-400">
          {STATUS_LABEL[report.status]}
        </span>
      </div>

      <dl className="mt-3 space-y-1 text-[13px]">
        <Row
          label="대상"
          value={who(report.targetUserNickname, report.targetUserId)}
        />
        <Row
          label="신고자"
          value={who(report.reporterNickname, report.reporterId)}
        />
        <Row label="접수" value={formatDateTimeMinute(report.createdAt)} />
        <Row
          label="과거 제재"
          value={`${report.pastPenaltyCount}건 (다음 ${report.pastPenaltyCount + 1}차)`}
        />
      </dl>

      {report.reportedContent && (
        <p className="mt-3 rounded-xl bg-gray-50 px-3 py-2 text-[13px] break-all text-gray-700">
          {report.reportedContent}
        </p>
      )}
      {report.detail && (
        <p className="mt-2 text-[13px] text-gray-600">“{report.detail}”</p>
      )}

      {report.status === "PENDING" ? (
        open ? (
          <DecideForm
            report={report}
            onDone={() => {
              setOpen(false);
              onDecided();
            }}
            onCancel={() => setOpen(false)}
          />
        ) : (
          <Button type="button" onClick={() => setOpen(true)} className="mt-4">
            판정하기
          </Button>
        )
      ) : (
        <div className="mt-3">
          {report.penalties.length > 0 ? (
            <ul className="space-y-1 text-[13px] text-gray-800">
              {report.penalties.map((penalty) => (
                <li key={penalty.id}>{describePenalty(penalty)}</li>
              ))}
            </ul>
          ) : (
            report.status === "REJECTED" && (
              <p className="text-[13px] text-gray-500">제재 없음</p>
            )
          )}
          <p className="mt-1 text-[12px] text-gray-400">
            {report.decidedAt
              ? `${formatDateTimeMinute(report.decidedAt)} 판정`
              : "판정 완료"}
          </p>
        </div>
      )}
    </li>
  );
}

/** 제재 한 줄. 종류마다 채워지는 값이 달라서 붙는 설명이 다르다. */
function describePenalty(penalty: PenaltyResponse): string {
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

/** 닉네임을 앞세우고 식별자를 괄호로 덧붙인다. 관리자가 사람을 알아봐야 한다. */
function who(nickname: string | null, userId: number): string {
  return nickname ? `${nickname} (#${userId})` : `#${userId}`;
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <dt className="text-gray-400">{label}</dt>
      <dd className="text-gray-700">{value}</dd>
    </div>
  );
}

function DecideForm({
  report,
  onDone,
  onCancel,
}: {
  report: ReportDetailResponse;
  onDone: () => void;
  onCancel: () => void;
}) {
  const [picked, setPicked] = useState<PenaltyType[]>([]);
  const [days, setDays] = useState("3");
  const [amount, setAmount] = useState("100");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function toggle(type: PenaltyType) {
    setPicked((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    );
  }

  function run(accept: boolean) {
    if (accept && picked.length === 0) {
      setError("승인하려면 제재를 하나 이상 선택해 주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    decideReport(report.id, {
      accept,
      penalties: accept ? toCommands(picked, days, amount) : [],
    })
      .then(onDone)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setSaving(false));
  }

  return (
    <div className="mt-4 border-t border-gray-100 pt-4">
      <p className="mb-2 text-[14px] font-semibold text-gray-900">제재 선택</p>
      <div className="space-y-2">
        {PENALTY_TYPES.map((type) => (
          <label
            key={type}
            className={`flex items-center gap-3 rounded-xl border px-4 py-2.5 text-[14px] ${
              picked.includes(type)
                ? "border-purple-400 bg-purple-50 text-gray-900"
                : "border-gray-200 text-gray-600"
            }`}
          >
            <input
              type="checkbox"
              checked={picked.includes(type)}
              onChange={() => toggle(type)}
              className="accent-purple-500"
            />
            {PENALTY_LABEL[type]}
          </label>
        ))}
      </div>

      {picked.includes("SUSPENSION") && (
        <TextField
          label="정지 일수"
          type="number"
          min={1}
          max={365}
          value={days}
          onChange={(e) => setDays(e.target.value)}
          className="mt-3"
        />
      )}
      {picked.includes("POINT_FORFEIT") && (
        <TextField
          label="압수 포인트"
          type="number"
          min={1}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="mt-3"
        />
      )}

      <FormAlert message={error} />

      <div className="mt-4 flex gap-2">
        <Button type="button" onClick={() => run(true)} loading={saving}>
          승인
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
      <button
        type="button"
        onClick={onCancel}
        className="mt-2 w-full text-[13px] text-gray-400"
      >
        취소
      </button>
    </div>
  );
}

function DirectPenaltyForm({ onDone }: { onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [detail, setDetail] = useState("");
  const [picked, setPicked] = useState<PenaltyType[]>([]);
  const [days, setDays] = useState("3");
  const [amount, setAmount] = useState("100");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  function toggle(type: PenaltyType) {
    setPicked((prev) =>
      prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type],
    );
  }

  function submit() {
    if (picked.length === 0) {
      setError("제재를 하나 이상 선택해 주세요.");
      return;
    }

    setSaving(true);
    setError(null);
    penalizeDirectly({
      email: email.trim(),
      detail: detail.trim(),
      penalties: toCommands(picked, days, amount),
    })
      .then(() => {
        setEmail("");
        setDetail("");
        setPicked([]);
        setOpen(false);
        onDone();
      })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setSaving(false));
  }

  if (!open) {
    return (
      <Button
        type="button"
        variant="secondary"
        onClick={() => setOpen(true)}
        className="mt-4"
      >
        신고 없이 제재
      </Button>
    );
  }

  return (
    <div className="mt-4 rounded-2xl border border-gray-200 p-4">
      <p className="text-[15px] font-bold text-gray-900">신고 없이 제재</p>
      <p className="mt-1 mb-3 text-[12px] text-gray-500">
        승인된 신고가 함께 만들어져 이의제기를 받을 수 있다. 콘텐츠 조치는
        나가지 않는다 — 닉네임 초기화가 필요하면 사용자 신고를 접수해 승인한다.
      </p>

      <TextField
        label="대상 이메일"
        type="email"
        placeholder="troll@gommit.local"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />
      <TextField
        label="사유"
        value={detail}
        maxLength={500}
        onChange={(e) => setDetail(e.target.value)}
        className="mt-3"
      />

      <p className="mt-4 mb-2 text-[14px] font-semibold text-gray-900">
        제재 선택
      </p>
      <div className="space-y-2">
        {PENALTY_TYPES.map((type) => (
          <label
            key={type}
            className={`flex items-center gap-3 rounded-xl border px-4 py-2.5 text-[14px] ${
              picked.includes(type)
                ? "border-purple-400 bg-purple-50 text-gray-900"
                : "border-gray-200 text-gray-600"
            }`}
          >
            <input
              type="checkbox"
              checked={picked.includes(type)}
              onChange={() => toggle(type)}
              className="accent-purple-500"
            />
            {PENALTY_LABEL[type]}
          </label>
        ))}
      </div>

      {picked.includes("SUSPENSION") && (
        <TextField
          label="정지 일수"
          type="number"
          min={1}
          max={365}
          value={days}
          onChange={(e) => setDays(e.target.value)}
          className="mt-3"
        />
      )}
      {picked.includes("POINT_FORFEIT") && (
        <TextField
          label="압수 포인트"
          type="number"
          min={1}
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="mt-3"
        />
      )}

      <FormAlert message={error} />

      <div className="mt-4 flex gap-2">
        <Button
          type="button"
          onClick={submit}
          loading={saving}
          disabled={email.trim() === "" || detail.trim() === ""}
        >
          제재
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => setOpen(false)}
          loading={saving}
        >
          닫기
        </Button>
      </div>
    </div>
  );
}

/** 고른 제재를 서버가 받는 모양으로 바꾼다. 값은 해당 종류에만 실린다. */
function toCommands(
  picked: PenaltyType[],
  days: string,
  amount: string,
): PenaltyCommand[] {
  return picked.map((penaltyType) => ({
    penaltyType,
    suspensionDays: penaltyType === "SUSPENSION" ? Number(days) : null,
    amount: penaltyType === "POINT_FORFEIT" ? Number(amount) : null,
  }));
}

function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  switch (error.code) {
    case "REPORT_ALREADY_DECIDED":
      return "이미 판정이 끝난 신고예요. 목록을 새로고침해 주세요.";
    case "PENALTY_REQUIRED":
      return "승인하려면 제재를 하나 이상 선택해 주세요.";
    case "INVALID_SUSPENSION_DAYS":
      return "정지 일수는 1일 이상 365일 이하여야 해요.";
    case "INVALID_FORFEIT_AMOUNT":
      return "압수 금액은 1 이상이어야 해요.";
    case "USER_NOT_FOUND":
      return "그 사용자를 찾을 수 없어요.";
    case "REPORT_NOT_FOUND":
      return "그 신고를 찾을 수 없어요.";
    default:
      return error.message;
  }
}

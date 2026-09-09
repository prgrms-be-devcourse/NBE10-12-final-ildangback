import { useRef, useState } from "react";
import cycle from "../../../assets/icons/material_symbols_cycle_rounded.webp";
import { ApiError } from "../../../shared/api/client";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { groupErrorMessage } from "../../group/errors";
import { chooseExtension } from "../api";
import type { ExtensionChoiceRequest, ExtensionChoiceResponse } from "../types";

export function ExtensionChoicePanel({
  challengeId,
  endDate,
}: {
  challengeId: number;
  endDate: string;
}) {
  const [result, setResult] = useState<ExtensionChoiceResponse | null>(null);
  const [pending, setPending] = useState(false);
  const [closed, setClosed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);
  const { showToast } = useToast();
  // The service accepts changes through endDate - 2 days, inclusive.
  const deadline = new Date(`${endDate}T00:00:00Z`);
  deadline.setUTCDate(deadline.getUTCDate() - 2);
  const deadlineLabel = Number.isNaN(deadline.getTime())
    ? null
    : deadline.toISOString().slice(0, 10);
  const total = result
    ? result.extendCount + result.declineCount + result.pendingCount
    : 0;
  const voted = result ? result.extendCount + result.declineCount : 0;
  // Status response extensionAvailable is currently a constant false TODO.
  // This panel is mounted only for ACTIVE challenges; mirror the existing PUT
  // service's date window and let its response enforce membership and cutoff.
  const today = new Date();
  const todayLabel = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const available = deadlineLabel !== null && todayLabel <= deadlineLabel;
  const canChoose = available && !closed && !pending;
  const save = async (choice: ExtensionChoiceRequest["choice"]) => {
    if (busy.current || !canChoose) return;
    busy.current = true;
    setPending(true);
    setError(null);
    try {
      setResult(await chooseExtension(challengeId, { choice }));
      showToast("연장 의사를 저장했어요.");
    } catch (err) {
      setError(groupErrorMessage(err));
      if (
        err instanceof ApiError &&
        ["EXTENSION_CHOICE_CLOSED", "EXTENSION_CHOICE_NOT_AVAILABLE"].includes(
          err.code,
        )
      )
        setClosed(true);
    } finally {
      busy.current = false;
      setPending(false);
    }
  };
  return (
    <section className="space-y-4 rounded-2xl border border-purple-300 p-5">
      <h2 className="flex items-center gap-2 font-bold">
        <img src={cycle} alt="" className="h-5 w-5 object-contain" />
        다음 시즌 연장 투표
      </h2>
      {deadlineLabel && (
        <p className="text-xs text-purple-500">{deadlineLabel} 마감</p>
      )}
      {result && (
        <div className="space-y-2">
          <p className="text-xs text-gray-500">마지막 저장 시점의 투표 현황</p>
          <progress
            aria-label="연장 투표 참여율"
            value={voted}
            max={Math.max(1, total)}
            className="h-2 w-full overflow-hidden rounded-full [&::-webkit-progress-bar]:bg-purple-50 [&::-webkit-progress-value]:bg-purple-500 [&::-moz-progress-bar]:bg-purple-500"
          />
          <p className="text-right text-sm text-purple-700">
            {voted} / {total}
          </p>
          <p role="status" className="text-xs text-purple-700">
            내 선택: {result.choice === "EXTEND" ? "찬성" : "반대"} · 찬성{" "}
            {result.extendCount}명 · 반대 {result.declineCount}명
          </p>
        </div>
      )}
      <p className="text-xs leading-relaxed text-gray-500">
        {!available || closed
          ? "현재 연장 투표가 마감되었거나 참여할 수 없는 상태예요."
          : "마감 전에는 선택을 변경할 수 있어요. 다음 시즌에 참여하려면 찬성을 선택해주세요."}
      </p>
      <div className="grid grid-cols-2 gap-3">
        <Button
          variant={result?.choice === "EXTEND" ? "primary" : "secondary"}
          aria-pressed={result?.choice === "EXTEND"}
          disabled={!canChoose}
          onClick={() => void save("EXTEND")}
        >
          찬성
        </Button>
        <Button
          variant={result?.choice === "DECLINE" ? "primary" : "secondary"}
          aria-pressed={result?.choice === "DECLINE"}
          disabled={!canChoose}
          onClick={() => void save("DECLINE")}
        >
          반대
        </Button>
      </div>
      {pending && (
        <p role="status" className="text-xs text-gray-500">
          저장 중…
        </p>
      )}
      <FormAlert message={error} />
    </section>
  );
}

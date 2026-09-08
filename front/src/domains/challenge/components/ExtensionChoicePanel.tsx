import { useRef, useState } from "react";
import { ApiError } from "../../../shared/api/client";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { groupErrorMessage } from "../../group/errors";
import { chooseExtension } from "../api";
import type {
  ExtensionChoice,
  ExtensionChoiceRequest,
  ExtensionChoiceResponse,
} from "../types";

// Wire extensionChoice from the server once its read-response contract is confirmed.
// Missing data must never be treated as PENDING.
export function ExtensionChoicePanel({
  challengeId,
  extensionChoice,
}: {
  challengeId: number;
  extensionChoice?: ExtensionChoice;
}) {
  const [choice, setChoice] = useState<ExtensionChoiceRequest["choice"] | null>(
    null,
  );
  const [result, setResult] = useState<ExtensionChoiceResponse | null>(null);
  const [pending, setPending] = useState(false);
  const [closed, setClosed] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);
  const { showToast } = useToast();
  const committed = useRef(false);
  const currentChoice = result?.choice ?? extensionChoice;
  const statusLabel =
    currentChoice === "PENDING"
      ? "미참여"
      : currentChoice === "EXTEND"
        ? "참여"
        : currentChoice === "DECLINE"
          ? "거절"
          : "확인 대기";
  const completed = currentChoice === "EXTEND" || currentChoice === "DECLINE";
  const canChoose =
    currentChoice === "PENDING" && !pending && !closed && !completed;
  const save = async () => {
    if (!choice || busy.current || committed.current || !canChoose) return;
    busy.current = true;
    setPending(true);
    setError(null);
    try {
      const response = await chooseExtension(challengeId, { choice });
      committed.current = true;
      setClosed(true);
      setResult(response);
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
    <details className="group rounded-2xl border border-purple-200 p-4">
      <summary className="flex cursor-pointer list-none items-center gap-3 rounded focus-visible:outline-2 focus-visible:outline-purple-500 [&::-webkit-details-marker]:hidden">
        <span className="flex-1 font-bold">다음 시즌 참여</span>
        <span role="status" className="text-sm font-semibold text-purple-700">
          {statusLabel}
        </span>
        <span
          aria-hidden="true"
          className="text-purple-500 transition-transform group-open:rotate-90"
        >
          ›
        </span>
      </summary>
      <div className="mt-4 space-y-3">
        {currentChoice === "PENDING" && (
          <p className="text-sm text-gray-500">
            아직 다음 시즌 참여 여부를 선택하지 않았어요.
          </p>
        )}
        <p className="text-xs leading-relaxed text-gray-500">
          연장 의사는 한 번만 저장할 수 있으며 저장 후에는 변경할 수 없어요.
          연장하지 않으면 다음 시즌에 참여하지 않아요.
        </p>
        <fieldset disabled={!canChoose} className="flex flex-wrap gap-4">
          {(
            [
              { value: "EXTEND", label: "참여" },
              { value: "DECLINE", label: "거절" },
            ] as const
          ).map((option) => (
            <label
              key={option.value}
              className="flex items-center gap-2 py-2 text-sm"
            >
              <input
                type="radio"
                name="extension-choice"
                checked={(completed ? currentChoice : choice) === option.value}
                onChange={() => setChoice(option.value)}
                className="accent-purple-500"
              />
              {option.label}
            </label>
          ))}
        </fieldset>
        {currentChoice === undefined && (
          <p role="status" className="text-sm text-gray-500">
            기존 연장 선택을 확인하는 기능이 준비 중이에요. 상태 확인 전에는
            선택할 수 없어요.
          </p>
        )}
        {completed && (
          <p role="status" className="text-sm font-semibold text-purple-700">
            선택 완료: {statusLabel}. 저장한 선택은 변경할 수 없어요.
          </p>
        )}
        <FormAlert message={error} />
        <Button
          loading={pending}
          disabled={!choice || !canChoose}
          onClick={save}
        >
          연장 의사 저장
        </Button>
        {result && (
          <p role="status" className="text-xs leading-relaxed text-purple-700">
            이번에 저장한 선택: {statusLabel}
            <br />
            저장 시점 현황: 연장 {result.extendCount}명 · 미연장{" "}
            {result.declineCount}명 · 미선택 {result.pendingCount}명
          </p>
        )}
      </div>
    </details>
  );
}

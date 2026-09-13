import { CaretDownIcon } from "@phosphor-icons/react";
import { useRef, useState } from "react";
import cycle from "../../../assets/icons/material_symbols_cycle_rounded.webp";
import { ApiError } from "../../../shared/api/client";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { groupErrorMessage } from "../../group/errors";
import { chooseExtension } from "../api";
import type {
  ExtensionChoice,
  ExtensionChoiceRequest,
  MemberTodayStatusResponse,
} from "../types";

export function ExtensionChoicePanel({
  challengeId,
  endDate,
  members,
  currentUserId,
  onSaved,
}: {
  challengeId: number;
  endDate: string;
  members?: MemberTodayStatusResponse[];
  currentUserId?: number;
  onSaved: () => void;
}) {
  const validChoice = (value: unknown): value is ExtensionChoice =>
    value === "PENDING" || value === "EXTEND" || value === "DECLINE";
  const memberChoice = members?.find(
    (member) => member.userId === currentUserId,
  )?.extensionChoice;
  const serverChoice = validChoice(memberChoice) ? memberChoice : undefined;
  const serverCounts =
    members?.length &&
    members.every((member) => validChoice(member.extensionChoice))
      ? {
          extendCount: members.filter(
            (member) => member.extensionChoice === "EXTEND",
          ).length,
          declineCount: members.filter(
            (member) => member.extensionChoice === "DECLINE",
          ).length,
          pendingCount: members.filter(
            (member) => member.extensionChoice === "PENDING",
          ).length,
        }
      : undefined;
  const result = serverCounts;
  const currentChoice = serverChoice;
  const choiceLabel =
    currentChoice === "EXTEND"
      ? "찬성"
      : currentChoice === "DECLINE"
        ? "반대"
        : currentChoice === "PENDING"
          ? "선택 안 함"
          : "확인 불가";
  const decided = currentChoice === "EXTEND" || currentChoice === "DECLINE";
  // 아직 안 고른 사람에게는 펼쳐 보여주고, 이미 고른 사람은 접어 둔다.
  // 멤버 응답이 첫 렌더 뒤에 오므로 useState 초기값으로는 판정할 수 없다.
  const [toggled, setToggled] = useState<boolean | null>(null);
  const open = toggled ?? !decided;
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
  // businessDate mirrors the backend's BusinessDayCutoff: KST (UTC+9) wall clock
  // minus a 4-hour cutoff, i.e. UTC+5h. Computed from the UTC epoch so it is
  // correct regardless of the browser's own timezone setting.
  const BUSINESS_DAY_OFFSET_MS = 5 * 60 * 60 * 1000;
  const today = new Date(new Date().getTime() + BUSINESS_DAY_OFFSET_MS);
  const todayLabel = today.toISOString().slice(0, 10);
  const available = deadlineLabel !== null && todayLabel <= deadlineLabel;
  const canChoose =
    available && !closed && !pending && currentChoice !== undefined;
  const save = async (choice: ExtensionChoiceRequest["choice"]) => {
    if (busy.current || !canChoose) return;
    busy.current = true;
    setPending(true);
    setError(null);
    try {
      await chooseExtension(challengeId, { choice });
      onSaved();
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
    <section className="rounded-2xl border border-purple-300 p-5">
      <button
        type="button"
        onClick={() => setToggled(!open)}
        aria-expanded={open}
        aria-controls="extension-vote-body"
        className="flex w-full items-center gap-2 rounded text-left font-bold focus-visible:ring-2 focus-visible:ring-purple-300 focus-visible:outline-none"
      >
        <img src={cycle} alt="" className="h-5 w-5 shrink-0 object-contain" />
        <span className="min-w-0 flex-1 truncate">다음 시즌 연장 투표</span>
        {!open &&
          (result ? (
            <span
              className={`shrink-0 rounded-full px-2 py-0.5 text-[11px] font-semibold ${decided ? "bg-purple-50 text-purple-700" : "bg-gray-100 text-gray-500"}`}
            >
              찬성 {result.extendCount} · 반대 {result.declineCount}
            </span>
          ) : (
            <span className="shrink-0 text-xs font-semibold text-purple-600">
              내 선택: {choiceLabel}
            </span>
          ))}
        <CaretDownIcon
          size={16}
          weight="bold"
          className={`shrink-0 text-gray-400 transition-transform ${open ? "rotate-180" : ""}`}
          aria-hidden
        />
      </button>

      {!open ? null : (
        <div id="extension-vote-body" className="mt-4 space-y-4">
          {deadlineLabel && (
            <p className="text-xs text-purple-500">{deadlineLabel} 마감</p>
          )}
          {result && (
            <div className="space-y-2">
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
                찬성 {result.extendCount}명 · 반대 {result.declineCount}명 ·
                선택 안 함 {result.pendingCount}명
              </p>
            </div>
          )}
          <p role="status" className="text-sm font-semibold text-purple-700">
            내 선택: {choiceLabel}
          </p>
          {currentChoice === undefined && (
            <p className="text-xs text-gray-500">
              저장된 투표 상태를 확인할 수 없어요.
            </p>
          )}
          <p className="text-xs leading-relaxed text-gray-500">
            {!available || closed
              ? "현재 연장 투표가 마감되었거나 참여할 수 없는 상태예요."
              : "마감 전에는 선택을 변경할 수 있어요. 다음 시즌에 참여하려면 찬성을 선택해주세요."}
          </p>
          <div className="grid grid-cols-2 gap-3">
            <Button
              variant={currentChoice === "EXTEND" ? "primary" : "secondary"}
              aria-pressed={currentChoice === "EXTEND"}
              disabled={!canChoose}
              onClick={() => void save("EXTEND")}
            >
              찬성
            </Button>
            <Button
              variant={currentChoice === "DECLINE" ? "primary" : "secondary"}
              aria-pressed={currentChoice === "DECLINE"}
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
        </div>
      )}
    </section>
  );
}

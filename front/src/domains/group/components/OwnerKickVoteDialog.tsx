import { useEffect, useRef, useState } from "react";
import { BottomSheet } from "../../../shared/ui/BottomSheet";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import {
  castOwnerKickVote,
  getOwnerKickVoteStatus,
  initiateOwnerKickVote,
} from "../api";
import { groupErrorMessage } from "../errors";
import type { KickVoteStatusResponse } from "../types";

interface Props {
  groupId: number;
  isOpen: boolean;
  onClose: () => void;
  onVoteConcludes?: () => void;
}

export function OwnerKickVoteDialog({
  groupId,
  isOpen,
  onClose,
  onVoteConcludes,
}: Props) {
  const [status, setStatus] = useState<KickVoteStatusResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [acting, setActing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const lock = useRef(false);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await getOwnerKickVoteStatus(groupId);
        if (!cancelled) setStatus(data);
      } catch (err: unknown) {
        if (!cancelled) setError(groupErrorMessage(err));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [isOpen, groupId]);

  const handleAction = async (fn: () => Promise<KickVoteStatusResponse>) => {
    if (lock.current) return;
    lock.current = true;
    setActing(true);
    setError(null);
    try {
      const newStatus = await fn();
      setStatus(newStatus);
      if (!newStatus.inProgress) {
        onVoteConcludes?.();
      }
    } catch (err) {
      setError(groupErrorMessage(err));
    } finally {
      lock.current = false;
      setActing(false);
    }
  };

  const passThreshold = status ? Math.floor(status.eligibleVoters / 2) + 1 : 0;
  const expiresLabel = status?.expiresAt
    ? new Date(status.expiresAt).toLocaleString("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
      })
    : null;

  return (
    <BottomSheet isOpen={isOpen} onClose={onClose} title="그룹장 강퇴 투표">
      <div className="space-y-3 pb-2">
        <FormAlert message={error} />

        {loading && (
          <p role="status" className="py-8 text-center text-sm text-gray-500">
            불러오는 중…
          </p>
        )}

        {/* 투표 없음 */}
        {!loading && status && !status.inProgress && (
          <>
            <p className="text-sm leading-relaxed text-gray-600">
              현재 진행 중인 그룹장 강퇴 투표가 없어요.
              <br />
              투표를 시작하면 자동으로 찬성 처리되며, 과반수 찬성 시 그룹장이
              즉시 교체돼요.
            </p>
            <p className="rounded-xl bg-purple-50 px-4 py-3 text-sm text-purple-700">
              ⏱ 투표는 개시 후 24시간 동안 유효해요.
            </p>
            <Button
              variant="danger"
              loading={acting}
              onClick={() => handleAction(() => initiateOwnerKickVote(groupId))}
            >
              투표 개시하기
            </Button>
            <Button variant="ghost" disabled={acting} onClick={onClose}>
              취소
            </Button>
          </>
        )}

        {/* 투표 진행 중, 아직 투표 안 함 */}
        {!loading && status?.inProgress && status.myChoice === "NONE" && (
          <>
            <VoteProgressBox
              status={status}
              passThreshold={passThreshold}
              expiresLabel={expiresLabel}
            />
            <p className="text-center text-sm text-gray-600">
              그룹장 강퇴에 동의하시나요?
            </p>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                loading={acting}
                onClick={() =>
                  handleAction(() => castOwnerKickVote(groupId, "DISAGREE"))
                }
              >
                반대
              </Button>
              <Button
                variant="danger"
                loading={acting}
                onClick={() =>
                  handleAction(() => castOwnerKickVote(groupId, "AGREE"))
                }
              >
                찬성
              </Button>
            </div>
            <Button variant="ghost" disabled={acting} onClick={onClose}>
              닫기
            </Button>
          </>
        )}

        {/* 투표 진행 중, 이미 투표함 */}
        {!loading && status?.inProgress && status.myChoice !== "NONE" && (
          <>
            <VoteProgressBox
              status={status}
              passThreshold={passThreshold}
              expiresLabel={expiresLabel}
            />
            <p className="rounded-xl bg-purple-50 px-4 py-3 text-center text-sm font-semibold text-purple-700">
              {status.myChoice === "AGREE"
                ? "✅ 찬성으로 투표했어요."
                : "✅ 반대로 투표했어요."}
            </p>
            <Button variant="ghost" onClick={onClose}>
              닫기
            </Button>
          </>
        )}
      </div>
    </BottomSheet>
  );
}

function VoteProgressBox({
  status,
  passThreshold,
  expiresLabel,
}: {
  status: KickVoteStatusResponse;
  passThreshold: number;
  expiresLabel: string | null;
}) {
  const agreeRatio =
    status.eligibleVoters > 0
      ? (status.agreeCount / status.eligibleVoters) * 100
      : 0;
  const disagreeRatio =
    status.eligibleVoters > 0
      ? (status.disagreeCount / status.eligibleVoters) * 100
      : 0;

  return (
    <div className="space-y-2 rounded-xl bg-purple-50 px-4 py-3">
      <div className="flex justify-between text-xs text-gray-400">
        <span>투표 가능 인원: {status.eligibleVoters}명</span>
        <span>가결 기준: {passThreshold}명 이상</span>
      </div>
      <div className="flex justify-between text-sm font-semibold">
        <span className="text-purple-600">찬성 {status.agreeCount}명</span>
        <span className="text-red-400">반대 {status.disagreeCount}명</span>
      </div>
      <div className="flex h-2.5 overflow-hidden rounded-full bg-purple-200">
        <div
          className="bg-purple-500 transition-all"
          style={{ width: `${agreeRatio}%` }}
        />
        <div
          className="bg-red-400 transition-all"
          style={{ width: `${disagreeRatio}%` }}
        />
      </div>
      {expiresLabel && (
        <p className="text-center text-xs text-gray-400">
          {expiresLabel}에 만료
        </p>
      )}
    </div>
  );
}

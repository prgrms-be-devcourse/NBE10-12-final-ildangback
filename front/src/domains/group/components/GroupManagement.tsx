import { useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { getInviteCode, leaveGroup } from "../api";
import { groupErrorMessage } from "../errors";
import type { GroupDetailResponse } from "../types";
import { ConfirmActionDialog } from "./ConfirmActionDialog";

export function GroupManagement({ detail }: { detail: GroupDetailResponse }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [leaving, setLeaving] = useState(false);
  const [code, setCode] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (!detail.members.some((member) => member.userId === user?.id)) return null;
  const owner = detail.group.ownerId === user?.id;
  const invite = async () => {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      setCode((await getInviteCode(detail.group.id)).inviteCode);
    } catch (err) {
      setError(groupErrorMessage(err));
    } finally {
      setPending(false);
    }
  };
  const copyCode = async () => {
    if (!code || !owner) return;
    try {
      await navigator.clipboard.writeText(code);
      showToast("초대코드를 복사했어요.");
    } catch {
      // Clipboard access may be denied; keep the selectable code visible.
    }
  };
  const shareInvite = async () => {
    if (!code || !owner) return;
    const url = new URL("/join-by-code", window.location.origin);
    url.searchParams.set("code", code);
    try {
      if (typeof navigator.share === "function") {
        await navigator.share({ title: "그룹 초대", url: url.toString() });
        showToast("초대 링크를 공유했어요.");
      } else {
        await navigator.clipboard.writeText(url.toString());
        showToast("초대 링크를 복사했어요.");
      }
    } catch {
      // Cancellation and sharing failures do not interrupt group management.
    }
  };
  return (
    <section className="space-y-3 rounded-2xl border border-purple-200 p-4">
      <h2 className="font-bold">그룹 관리</h2>
      {owner && detail.group.visibility === "CODE_ONLY" && (
        <>
          <Button variant="secondary" loading={pending} onClick={invite}>
            초대코드 조회
          </Button>
          {code && (
            <div className="space-y-3">
              <p className="rounded-xl bg-purple-50 p-4 text-center text-xl font-bold tracking-widest text-purple-700 select-all">
                {code}
              </p>
              <div className="grid grid-cols-2 gap-2">
                <Button variant="secondary" onClick={copyCode}>
                  코드 복사
                </Button>
                <Button variant="secondary" onClick={shareInvite}>
                  공유하기
                </Button>
              </div>
            </div>
          )}
        </>
      )}
      <FormAlert message={error} />
      <Button
        variant="danger"
        disabled={owner}
        onClick={() => setLeaving(true)}
      >
        그룹 나가기
      </Button>
      {owner && (
        <p className="text-xs leading-relaxed text-gray-500">
          그룹장은 그룹장 권한을 위임한 뒤 나갈 수 있어요. 첫 시즌 또는 진행
          중인 시즌에서 위임하면 그룹 전체의 관리 권한도 함께 이전됩니다. 이후
          시즌의 시작 대기 상태에서는 해당 시즌의 그룹장 권한만 이전돼요.
        </p>
      )}
      {leaving && (
        <ConfirmActionDialog
          title="그룹에서 나갈까요?"
          description="나가면 현재 챌린지 참여도 종료됩니다. 참여 이력이 있는 그룹에는 다시 가입할 수 없어요."
          confirmLabel="나가기"
          onClose={() => setLeaving(false)}
          onConfirm={async () => {
            await leaveGroup(detail.group.id);
            showToast("그룹에서 나왔어요.");
            navigate("/challenges", { replace: true });
          }}
        />
      )}
    </section>
  );
}

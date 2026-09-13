import { useState } from "react";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { getInviteCode } from "../api";
import { groupErrorMessage } from "../errors";
import type { GroupDetailResponse } from "../types";

export function GroupInvite({ detail }: { detail: GroupDetailResponse }) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [code, setCode] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  if (
    detail.group.ownerId !== user?.id ||
    detail.group.visibility !== "CODE_ONLY"
  )
    return null;
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
    if (!code) return;
    try {
      await navigator.clipboard.writeText(code);
      showToast("초대코드를 복사했어요.");
    } catch {
      // Clipboard access may be denied; keep the selectable code visible.
    }
  };
  const shareInvite = async () => {
    if (!code) return;
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
      <h2 className="font-bold">그룹 초대</h2>
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
      <FormAlert message={error} />
    </section>
  );
}

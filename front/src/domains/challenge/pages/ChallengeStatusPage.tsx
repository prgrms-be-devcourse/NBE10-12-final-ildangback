import { useCallback, useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError } from "../../../shared/api/client";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import { getGroup, kickGroupMember } from "../../group/api";
import { ConfirmActionDialog } from "../../group/components/ConfirmActionDialog";
import { GroupManagement } from "../../group/components/GroupManagement";
import { useResource } from "../../group/hooks/useResource";
import { getChallenge, getChallengeMembers, delegateOwner } from "../api";
import { ChallengeSettingsEditor } from "../components/ChallengeSettingsEditor";
import { ExtensionChoicePanel } from "../components/ExtensionChoicePanel";
import {
  ChallengeProgress,
  ChallengeRulesCard,
  StatusBadge,
} from "../components/ChallengeInfo";
import type { ChallengeStatusResponse } from "../types";

export function ChallengeStatusPage() {
  const { challengeId } = useParams();
  return <ChallengeStatusContent key={challengeId} id={Number(challengeId)} />;
}
function ChallengeStatusContent({ id }: { id: number }) {
  const loader = useCallback(
    () =>
      Number.isSafeInteger(id) && id > 0
        ? getChallenge(id)
        : Promise.reject(new Error("invalid id")),
    [id],
  );
  const { data, error, loading, retry } = useResource(loader);
  return (
    <>
      <TopBar title="챌린지 현황" />
      <main className="space-y-5 px-5 pt-5 pb-10">
        {loading && (
          <p role="status" className="py-10 text-center text-sm text-gray-500">
            챌린지를 불러오는 중…
          </p>
        )}
        {error && (
          <>
            <FormAlert message={error} />
            <Button variant="secondary" onClick={retry}>
              다시 시도
            </Button>
          </>
        )}
        {data && <ChallengeContent data={data} onChanged={retry} />}
        <Link
          to="/challenges"
          className="block py-2 text-center text-sm text-purple-500"
        >
          내 그룹으로
        </Link>
      </main>
    </>
  );
}
function ChallengeContent({
  data,
  onChanged,
}: {
  data: ChallengeStatusResponse;
  onChanged: () => void;
}) {
  const { challenge } = data;
  const { user } = useAuth();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [action, setAction] = useState<{
    type: "delegate" | "kick";
    userId: number;
    nickname: string;
  } | null>(null);
  const owner = challenge.ownerId === user?.id;
  const groupLoader = useCallback(async () => {
    try {
      return await getGroup(challenge.groupId);
    } catch (error) {
      // Former group members may still read an ENDED season. Group access must
      // not prevent the historical season from rendering.
      if (
        error instanceof ApiError &&
        ["NOT_GROUP_MEMBER", "GROUP_NOT_FOUND"].includes(error.code)
      )
        return null;
      throw error;
    }
  }, [challenge.groupId]);
  const membersLoader = useCallback(
    () => getChallengeMembers(challenge.id),
    [challenge.id],
  );
  const group = useResource(groupLoader);
  const members = useResource(membersLoader);
  return (
    <>
      <section className="space-y-4 rounded-2xl border border-purple-200 p-4">
        <div className="flex items-start justify-between gap-3">
          <h1 className="min-w-0 text-xl font-bold wrap-anywhere">
            {group.data?.group.name ?? `시즌 ${challenge.seqNo}`}
          </h1>
          <StatusBadge status={challenge.status} />
        </div>
        {group.data?.group.description && (
          <p className="text-sm leading-relaxed wrap-anywhere whitespace-pre-wrap">
            {group.data.group.description}
          </p>
        )}
        <p className="text-xs text-gray-500">
          시즌 {challenge.seqNo} · {data.participantCount}명 참여
        </p>
        <ChallengeProgress {...data} />
        {challenge.status === "READY" && (
          <p className="rounded-xl bg-purple-50 p-3 text-sm text-purple-700">
            {challenge.startDate} 시작 예정이에요.
          </p>
        )}
        {challenge.status === "ENDED" && (
          <p className="rounded-xl bg-gray-100 p-3 text-sm text-gray-500">
            종료된 챌린지예요.
          </p>
        )}
        {challenge.status === "ACTIVE" && !data.isCheckInDay && (
          <p className="rounded-xl bg-purple-50 p-3 text-sm text-purple-700">
            오늘은 인증 예정일이 아니에요.
          </p>
        )}
      </section>
      {group.error && (
        <div className="space-y-2">
          <FormAlert message={group.error} />
          <Button variant="secondary" onClick={group.retry}>
            그룹 정보 다시 불러오기
          </Button>
        </div>
      )}
      <div className="border-b border-purple-200 py-2 text-center text-sm font-semibold text-purple-500">
        현황
      </div>
      <ChallengeRulesCard settings={challenge} />
      {owner &&
        challenge.status === "READY" &&
        (editing ? (
          <ChallengeSettingsEditor
            challenge={challenge}
            onSaved={onChanged}
            onClose={() => setEditing(false)}
          />
        ) : (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            챌린지 설정 수정
          </Button>
        ))}
      <section className="rounded-2xl border border-purple-200 p-4">
        <h2 className="font-bold">시즌 멤버</h2>
        {members.loading && (
          <p role="status" className="mt-4 text-sm text-gray-500">
            멤버를 불러오는 중…
          </p>
        )}
        {members.error && (
          <div className="mt-4 space-y-2">
            <FormAlert message={members.error} />
            <Button variant="secondary" onClick={members.retry}>
              다시 시도
            </Button>
          </div>
        )}
        {members.data && (
          <ul className="mt-4 flex flex-wrap gap-2">
            {members.data.map((member) => (
              <li
                key={member.userId}
                className="max-w-full rounded-xl bg-purple-50 px-3 py-2 text-sm wrap-anywhere"
              >
                {member.nickname}
                {member.userId === challenge.ownerId && (
                  <span className="ml-2 text-xs text-purple-500">시즌장</span>
                )}
                {owner &&
                  member.userId !== user?.id &&
                  challenge.status !== "ENDED" && (
                    <button
                      type="button"
                      className="ml-2 py-1 text-xs text-purple-700 underline"
                      onClick={() =>
                        setAction({
                          type: "delegate",
                          userId: member.userId,
                          nickname: member.nickname,
                        })
                      }
                    >
                      시즌장 위임
                    </button>
                  )}
                {group.data &&
                  group.data.group.ownerId === user?.id &&
                  group.data.currentChallenge?.status === "ACTIVE" &&
                  challenge.id === group.data.currentChallenge.id &&
                  member.userId !== group.data.group.ownerId &&
                  group.data.members.some(
                    (item) => item.userId === member.userId,
                  ) && (
                    <button
                      type="button"
                      className="ml-2 py-1 text-xs text-red-600 underline"
                      onClick={() =>
                        setAction({
                          type: "kick",
                          userId: member.userId,
                          nickname: member.nickname,
                        })
                      }
                    >
                      강퇴
                    </button>
                  )}
              </li>
            ))}
          </ul>
        )}
      </section>
      {group.data && (
        <>
          <Link
            className="block text-center text-sm text-purple-500"
            to={`/challenges/groups/${challenge.groupId}`}
          >
            그룹 정보 보기
          </Link>
          <GroupManagement detail={group.data} />
        </>
      )}
      {challenge.status === "ACTIVE" && (
        <ExtensionChoicePanel challengeId={challenge.id} />
      )}
      {action && (
        <ConfirmActionDialog
          title={
            action.type === "delegate"
              ? "시즌장을 위임할까요?"
              : "그룹원을 강퇴할까요?"
          }
          description={
            action.type === "delegate"
              ? `${action.nickname} 님에게 시즌장 권한을 넘깁니다. 첫 시즌 또는 진행 중인 시즌에서는 그룹장도 변경됩니다.`
              : `${action.nickname} 님의 그룹 및 현재 시즌 참여를 종료합니다. 다시 가입할 수 없어요.`
          }
          confirmLabel={action.type === "delegate" ? "위임하기" : "강퇴하기"}
          onClose={() => setAction(null)}
          onConfirm={async () => {
            if (action.type === "delegate") {
              await delegateOwner(challenge.id, {
                targetUserId: action.userId,
              });
              showToast("시즌장을 위임했어요.");
            } else {
              await kickGroupMember(challenge.groupId, action.userId);
              showToast("그룹원을 내보냈어요.");
            }
            onChanged();
          }}
        />
      )}
    </>
  );
}

import { useCallback, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  ChallengeRulesCard,
  StatusBadge,
} from "../../challenge/components/ChallengeInfo";
import { getGroup, joinPublicGroup } from "../api";
import { GroupManagement } from "../components/GroupManagement";
import { CATEGORY_LABEL } from "../constants";
import { groupErrorMessage } from "../errors";
import { useResource } from "../hooks/useResource";

export function GroupPreviewPage() {
  const { groupId } = useParams();
  return <GroupPreview key={groupId} id={Number(groupId)} />;
}
function GroupPreview({ id }: { id: number }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const { showToast } = useToast();
  const loader = useCallback(
    () =>
      Number.isSafeInteger(id) && id > 0
        ? getGroup(id)
        : Promise.reject(new Error("invalid id")),
    [id],
  );
  const resource = useResource(loader);
  const [joining, setJoining] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const join = async () => {
    if (lock.current) return;
    lock.current = true;
    setJoining(true);
    setError(null);
    try {
      const result = await joinPublicGroup(id);
      showToast("그룹에 참여했어요.");
      navigate(`/challenges/${result.challengeId}`, { replace: true });
    } catch (err) {
      setError(groupErrorMessage(err));
      resource.retry();
    } finally {
      lock.current = false;
      setJoining(false);
    }
  };
  const data = resource.data;
  const joined = data?.members.some((member) => member.userId === user?.id);
  const canJoin =
    data?.group.visibility === "PUBLIC" &&
    data.group.status === "READY" &&
    data.currentChallenge?.status === "READY" &&
    data.group.currentMembers < data.group.maxMembers &&
    !joined;
  return (
    <>
      <TopBar title="그룹 정보" />
      <main className="space-y-5 px-5 pt-5 pb-10">
        <FormAlert message={error} />
        {resource.loading && (
          <p role="status" className="py-10 text-center text-sm text-gray-500">
            그룹 정보를 불러오는 중…
          </p>
        )}
        {resource.error && (
          <>
            <FormAlert message={resource.error} />
            <Button variant="secondary" onClick={resource.retry}>
              다시 시도
            </Button>
          </>
        )}
        {data && (
          <>
            <section className="space-y-3 px-2">
              <div className="flex items-center gap-2">
                <span className="rounded-md border border-purple-300 px-2 py-1 text-xs text-purple-500">
                  {data.group.visibility === "PUBLIC"
                    ? "공개 그룹"
                    : "비공개 그룹"}
                </span>
                <StatusBadge status={data.group.status} />
              </div>
              <h1 className="text-2xl font-bold wrap-anywhere">
                {data.group.name}
              </h1>
              <p className="text-sm text-gray-500">
                {CATEGORY_LABEL[data.group.category]} ·{" "}
                {data.group.currentMembers} / {data.group.maxMembers}명
              </p>
              {data.group.description && (
                <p className="text-sm leading-relaxed wrap-anywhere whitespace-pre-wrap">
                  {data.group.description}
                </p>
              )}
            </section>
            {data.currentChallenge && (
              <ChallengeRulesCard settings={data.currentChallenge} />
            )}
            <section className="rounded-2xl border border-purple-200 p-4">
              <h2 className="font-bold">
                함께할 멤버 {data.group.currentMembers} /{" "}
                {data.group.maxMembers}
              </h2>
              <ul className="mt-4 flex flex-wrap gap-2">
                {data.members.map((member) => (
                  <li
                    key={member.id}
                    className="max-w-full rounded-xl bg-purple-50 px-3 py-2 text-sm wrap-anywhere"
                  >
                    {member.nickname}
                    {member.userId === data.group.ownerId && (
                      <span className="ml-2 text-xs text-purple-500">
                        그룹장
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </section>
            <GroupManagement detail={data} />
            {canJoin ? (
              <>
                <div className="rounded-2xl border border-purple-200 p-4 text-sm">
                  <h2 className="mb-2 font-bold">참여 전 확인</h2>
                  <p>{data.currentChallenge?.startDate}에 챌린지가 시작해요.</p>
                </div>
                <Button loading={joining} onClick={join}>
                  참여하기
                </Button>
                <p className="text-center text-xs text-purple-500">
                  참여하면 챌린지 규칙에 동의한 것으로 간주해요.
                </p>
              </>
            ) : joined && data.currentChallenge ? (
              <Link
                to={`/challenges/${data.currentChallenge.id}`}
                className="block rounded-xl bg-purple-500 py-4 text-center text-sm font-semibold text-white"
              >
                챌린지 현황 보기
              </Link>
            ) : (
              <p className="rounded-xl bg-purple-50 p-4 text-center text-sm text-gray-500">
                {data.group.currentMembers >= data.group.maxMembers
                  ? "그룹 정원이 가득 찼어요."
                  : "현재 참여할 수 없는 그룹이에요."}
              </p>
            )}
          </>
        )}
        <Link
          to="/challenges?tab=explore"
          className="block py-2 text-center text-sm text-purple-500"
        >
          그룹 탐색으로
        </Link>
      </main>
    </>
  );
}

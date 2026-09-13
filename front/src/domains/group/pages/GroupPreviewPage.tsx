import groupIllustration from "../../../assets/icons/image_119.webp";
import pencilIcon from "../../../assets/icons/boxicons_pencil.webp";
import { CharacterRenderer } from "../../user/components/CharacterRenderer";
import { getUserCharacters } from "../../user/api";
import peopleIcon from "../../../assets/icons/people.webp";
import { useCallback, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import {
  ChallengeRulesCard,
  StatusBadge,
} from "../../challenge/components/ChallengeInfo";
import { ChallengeSettingsEditor } from "../../challenge/components/ChallengeSettingsEditor";
import {
  delegateOwner,
  getChallenge,
  getChallengeMembers,
} from "../../challenge/api";
import type { ChallengeDetail } from "../../challenge/types";
import { getGroup, joinPublicGroup, kickGroupMember } from "../api";
import { ConfirmActionDialog } from "../components/ConfirmActionDialog";
import { GroupInvite } from "../components/GroupInvite";
import { CATEGORY_LABEL } from "../constants";
import { groupErrorMessage } from "../errors";
import { useResource } from "../hooks/useResource";
import type { GroupDetailResponse } from "../types";

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
  const characterLoader = useCallback(
    () =>
      getUserCharacters(
        data?.members
          .filter((member) => member.status === "ACTIVE")
          .map((member) => member.userId) ?? [],
      ),
    [data?.members],
  );
  const characters = useResource(characterLoader);
  const memberCharacters = data?.members
    .filter((member) => member.status === "ACTIVE")
    .map((member) => ({
      member,
      slots: characters.data?.[String(member.userId)],
    }));

  const joined = data?.members.some((member) => member.userId === user?.id);
  const canJoin =
    data?.group.visibility === "PUBLIC" &&
    data.group.status === "READY" &&
    data.currentChallenge?.status === "READY" &&
    data.group.currentMembers < data.group.maxMembers &&
    !joined;
  return (
    <>
      <TopBar title={joined ? "챌린지 정보" : "그룹 정보"} />
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
            <section className="flex items-start gap-3 px-1">
              <div className="min-w-0 flex-1 space-y-3">
                <div className="flex flex-wrap items-center gap-2">
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
              </div>
              <img
                src={groupIllustration}
                alt=""
                className="mt-5 w-24 shrink-0 object-contain min-[400px]:w-28 sm:w-36"
              />
            </section>
            {data.currentChallenge && (
              <ChallengeRulesCard settings={data.currentChallenge} />
            )}
            {joined && data.currentChallenge ? (
              <SeasonManagement
                detail={data}
                challengeId={data.currentChallenge.id}
                memberCharacters={memberCharacters ?? []}
                characters={characters}
                onChanged={resource.retry}
              />
            ) : (
              <MemberGrid
                title={`함께할 멤버 ${data.group.currentMembers} / ${data.group.maxMembers}`}
                detail={data}
                memberCharacters={memberCharacters ?? []}
                characters={characters}
              />
            )}
            <GroupInvite detail={data} />
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
            ) : joined ? null : (
              <p className="rounded-xl bg-purple-50 p-4 text-center text-sm text-gray-500">
                {data.group.currentMembers >= data.group.maxMembers
                  ? "그룹 정원이 가득 찼어요."
                  : "현재 참여할 수 없는 그룹이에요."}
              </p>
            )}
          </>
        )}
      </main>
    </>
  );
}

type MemberCharacter = {
  member: GroupDetailResponse["members"][number];
  slots: Parameters<typeof CharacterRenderer>[0]["slots"] | undefined;
};

// 그룹장만 쓰는 시즌 관리가 붙은 멤버 목록. 규칙 수정 · 위임 · 강퇴가 여기 모인다.
function SeasonManagement({
  detail,
  challengeId,
  memberCharacters,
  characters,
  onChanged,
}: {
  detail: GroupDetailResponse;
  challengeId: number;
  memberCharacters: MemberCharacter[];
  characters: ReturnType<typeof useResource<Record<string, unknown>>>;
  onChanged: () => void;
}) {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [delegating, setDelegating] = useState(false);
  const [action, setAction] = useState<{
    type: "delegate" | "kick";
    userId: number;
    nickname: string;
  } | null>(null);
  const challengeLoader = useCallback(
    () => getChallenge(challengeId),
    [challengeId],
  );
  const challenge = useResource(challengeLoader);
  const seasonMembersLoader = useCallback(
    () => getChallengeMembers(challengeId),
    [challengeId],
  );
  const seasonMembers = useResource(seasonMembersLoader);
  const season: ChallengeDetail | undefined = challenge.data?.challenge;
  const owner = season?.ownerId === user?.id;
  const canEdit = owner && season?.status === "READY";
  const inSeason = (userId: number) =>
    !!seasonMembers.data?.some((member) => member.userId === userId);
  const canDelegate =
    owner &&
    (season?.status === "READY" || season?.status === "ACTIVE") &&
    !!seasonMembers.data?.some((member) => member.userId !== user?.id);
  const canKick =
    detail.group.ownerId === user?.id &&
    detail.currentChallenge?.status === "ACTIVE";
  return (
    <>
      {canEdit &&
        season &&
        (editing ? (
          <ChallengeSettingsEditor
            challenge={season}
            onSaved={() => {
              challenge.retry();
              onChanged();
            }}
            onClose={() => setEditing(false)}
          />
        ) : (
          <Button
            variant="secondary"
            className="flex items-center justify-center gap-2"
            onClick={() => setEditing(true)}
          >
            <img
              src={pencilIcon}
              alt=""
              width={20}
              height={20}
              className="shrink-0 object-contain"
            />
            챌린지 설정 수정
          </Button>
        ))}
      <MemberGrid
        title={`그룹 멤버 ${detail.group.currentMembers} / ${detail.group.maxMembers}`}
        detail={detail}
        memberCharacters={memberCharacters}
        characters={characters}
        action={
          canDelegate && (
            <button
              type="button"
              onClick={() => setDelegating((value) => !value)}
              aria-pressed={delegating}
              className={`min-h-8 shrink-0 rounded-full border px-3 text-xs font-semibold whitespace-nowrap transition-colors ${
                delegating
                  ? "border-purple-500 bg-purple-500 text-white hover:bg-purple-600"
                  : "border-purple-300 bg-white text-purple-700 hover:bg-purple-50"
              }`}
            >
              {delegating ? "위임 취소" : "그룹장 위임"}
            </button>
          )
        }
        note={
          delegating ? (
            <p className="mt-2 text-xs text-purple-500">
              위임할 그룹원을 선택해주세요.
            </p>
          ) : null
        }
        renderControl={(member) => {
          if (delegating)
            return canDelegate &&
              member.userId !== user?.id &&
              inSeason(member.userId) ? (
              <button
                type="button"
                onClick={() => {
                  setAction({
                    type: "delegate",
                    userId: member.userId,
                    nickname: member.nickname,
                  });
                  setDelegating(false);
                }}
                aria-label={`${member.nickname} 님에게 그룹장 위임`}
                className="min-h-7 cursor-pointer rounded-full bg-purple-500 px-2.5 text-[11px] font-semibold whitespace-nowrap text-white hover:bg-purple-600"
              >
                위임하기
              </button>
            ) : null;
          return canKick && member.userId !== detail.group.ownerId ? (
            <button
              type="button"
              aria-label={`${member.nickname} 님 강퇴`}
              className="min-h-7 rounded-full border border-red-200 bg-white px-2.5 text-[11px] font-semibold whitespace-nowrap text-red-600 hover:bg-red-50"
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
          ) : null;
        }}
      />
      {seasonMembers.error && (
        <p role="alert" className="text-xs text-gray-500">
          시즌 참여자를 불러오지 못했어요. 위임은 잠시 뒤 다시 시도해주세요.
        </p>
      )}
      {action && (
        <ConfirmActionDialog
          title={
            action.type === "delegate"
              ? "그룹장을 위임할까요?"
              : "그룹원을 강퇴할까요?"
          }
          description={
            action.type === "delegate"
              ? `${action.nickname} 님에게 그룹장 권한을 넘깁니다. 첫 시즌 또는 진행 중인 시즌에서는 그룹 전체의 관리 권한도 함께 이전됩니다.`
              : `${action.nickname} 님의 그룹 및 현재 시즌 참여를 종료합니다. 다시 가입할 수 없어요.`
          }
          confirmLabel={action.type === "delegate" ? "위임하기" : "강퇴하기"}
          onClose={() => setAction(null)}
          onConfirm={async () => {
            if (action.type === "delegate") {
              await delegateOwner(challengeId, { targetUserId: action.userId });
              showToast("그룹장을 위임했어요.");
            } else {
              await kickGroupMember(detail.group.id, action.userId);
              showToast("그룹원을 내보냈어요.");
            }
            challenge.retry();
            seasonMembers.retry();
            onChanged();
          }}
        />
      )}
    </>
  );
}
function MemberGrid({
  title,
  detail,
  memberCharacters,
  characters,
  action,
  note,
  renderControl,
}: {
  title: string;
  detail: GroupDetailResponse;
  memberCharacters: MemberCharacter[];
  characters: ReturnType<typeof useResource<Record<string, unknown>>>;
  action?: React.ReactNode;
  note?: React.ReactNode;
  renderControl?: (
    member: GroupDetailResponse["members"][number],
  ) => React.ReactNode;
}) {
  return (
    <section className="rounded-2xl border border-purple-200 p-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="flex min-w-0 items-center gap-2 font-bold">
          <img
            src={peopleIcon}
            alt=""
            width={20}
            height={20}
            className="shrink-0 object-contain"
          />
          <span className="truncate">{title}</span>
        </h2>
        {action}
      </div>
      {note}
      {characters.loading && (
        <p role="status" className="mt-2 text-xs text-gray-500">
          캐릭터 정보를 불러오는 중…
        </p>
      )}
      {characters.error && (
        <div className="mt-2 space-y-2">
          <p role="alert" className="text-xs text-gray-500">
            캐릭터 정보를 불러오지 못했어요.
          </p>
          <Button variant="secondary" onClick={characters.retry}>
            캐릭터 다시 불러오기
          </Button>
        </div>
      )}
      <ul className="mt-4 grid grid-cols-3 gap-2 sm:grid-cols-4">
        {memberCharacters.map(({ member, slots }) => (
          <li
            key={member.id}
            className="flex min-w-0 flex-col items-center gap-1 rounded-xl bg-purple-50 px-1 py-3 text-center text-sm wrap-anywhere"
          >
            {slots && (
              <CharacterRenderer
                pose="DEFAULT"
                slots={slots}
                label={`${member.nickname} 캐릭터`}
                className="h-16 w-16"
              />
            )}
            <span className="w-full">{member.nickname}</span>
            {member.userId === detail.group.ownerId && (
              <span className="text-xs text-purple-500">그룹장</span>
            )}
            {renderControl?.(member)}
          </li>
        ))}
      </ul>
    </section>
  );
}

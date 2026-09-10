import type { GroupDetailResponse } from "../../group/types";
import { CaretLeftIcon, DotsThreeIcon } from "@phosphor-icons/react";
import { useNavigate } from "react-router";
import { ChallengeDashboard } from "../components/ChallengeDashboard";
import pencilIcon from "../../../assets/icons/boxicons_pencil.webp";
import peopleIcon from "../../../assets/icons/people.webp";
import type { ReactNode } from "react";
import chatIcon from "../../../assets/icons/ep_chat_dot_round.webp";
import { useCallback, useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError } from "../../../shared/api/client";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { TopBar } from "../../../shared/ui/TopBar";
import { getGroup, getGroupChallenges, kickGroupMember } from "../../group/api";
import { ConfirmActionDialog } from "../../group/components/ConfirmActionDialog";
import { GroupManagement } from "../../group/components/GroupManagement";
import { useResource } from "../../group/hooks/useResource";
import {
  getChallenge,
  getChallengeMembers,
  getChallengeCharacters,
  delegateOwner,
} from "../api";
import { ChallengeSettingsEditor } from "../components/ChallengeSettingsEditor";
import { ExtensionChoicePanel } from "../components/ExtensionChoicePanel";
import { ChallengeRulesCard } from "../components/ChallengeInfo";
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
      {!data && <TopBar title="챌린지 현황" />}
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
        {data && <SeasonBrowser initialData={data} />}
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
function SeasonBrowser({
  initialData,
}: {
  initialData: ChallengeStatusResponse;
}) {
  const groupLoader = useCallback(async () => {
    try {
      return await getGroup(initialData.challenge.groupId);
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
  }, [initialData.challenge.groupId]);
  const group = useResource(groupLoader);
  const [chosenChallengeId, setSelectedChallengeId] = useState<number | null>(
    null,
  );
  const seasonLoader = useCallback(
    () => getGroupChallenges(initialData.challenge.groupId),
    [initialData.challenge.groupId],
  );
  const seasons = useResource(seasonLoader);
  const selectedChallengeId =
    chosenChallengeId ??
    group.data?.currentChallenge?.id ??
    seasons.data?.[0]?.id ??
    initialData.challenge.id;
  // Wait for the list before choosing a fallback when no current season exists.
  const selectingInitialSeason =
    group.loading ||
    (chosenChallengeId === null &&
      !group.data?.currentChallenge &&
      seasons.loading);
  const detailLoader = useCallback(
    () =>
      selectingInitialSeason
        ? Promise.resolve(undefined)
        : getChallenge(selectedChallengeId),
    [selectedChallengeId, selectingInitialSeason],
  );
  const detail = useResource(detailLoader);
  const currentId = group.data?.currentChallenge?.id;
  const options = seasons.data?.length
    ? seasons.data
    : [group.data?.currentChallenge ?? initialData.challenge];
  const selection = (
    <div className="space-y-2">
      <label className="sr-only" htmlFor="challenge-season">
        시즌 선택
      </label>
      <select
        id="challenge-season"
        value={selectedChallengeId}
        disabled={selectingInitialSeason || !seasons.data?.length}
        onChange={(event) => setSelectedChallengeId(Number(event.target.value))}
        className="min-h-11 rounded-lg border border-purple-200 bg-white px-3 text-sm font-bold text-purple-700 disabled:opacity-60"
      >
        {!options.some((season) => season.id === selectedChallengeId) && (
          <option value={selectedChallengeId}>선택한 시즌</option>
        )}
        {options.map((season) => (
          <option key={season.id} value={season.id}>
            시즌 {season.seqNo}
          </option>
        ))}
      </select>
      {seasons.loading && (
        <p role="status" className="text-xs text-gray-500">
          시즌 목록을 불러오는 중…
        </p>
      )}
      {seasons.error && (
        <div className="space-y-2">
          <p role="alert" className="text-xs text-gray-500">
            시즌 목록을 불러오지 못했어요. 다시 시도해주세요.
          </p>
          <Button variant="secondary" onClick={seasons.retry}>
            시즌 목록 다시 불러오기
          </Button>
        </div>
      )}
    </div>
  );
  return detail.data ? (
    <ChallengeContent
      key={selectedChallengeId}
      data={detail.data}
      onChanged={detail.retry}
      isCurrent={currentId === selectedChallengeId}
      currentKnown={!!group.data}
      group={group}
      selection={selection}
    />
  ) : (
    <>
      {selection}
      {(selectingInitialSeason || detail.loading) && (
        <p role="status" className="py-10 text-center text-sm text-gray-500">
          선택한 시즌을 불러오는 중…
        </p>
      )}
      {detail.error && (
        <>
          <FormAlert message={detail.error} />
          <Button variant="secondary" onClick={detail.retry}>
            선택한 시즌 다시 불러오기
          </Button>
        </>
      )}
    </>
  );
}
function ChallengeContent({
  data,
  onChanged,
  isCurrent,
  currentKnown,
  selection,
  group,
}: {
  data: ChallengeStatusResponse;
  onChanged: () => void;
  isCurrent: boolean;
  currentKnown: boolean;
  selection: ReactNode;
  group: ReturnType<typeof useResource<GroupDetailResponse | null>>;
}) {
  const { challenge } = data;
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [showDetails, setShowDetails] = useState(false);
  const { user } = useAuth();
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [action, setAction] = useState<{
    type: "delegate" | "kick";
    userId: number;
    nickname: string;
  } | null>(null);
  const owner = challenge.ownerId === user?.id;
  const membersLoader = useCallback(
    () => getChallengeMembers(challenge.id),
    [challenge.id],
  );
  const members = useResource(membersLoader);
  const charactersLoader = useCallback(
    () => getChallengeCharacters(challenge.id),
    [challenge.id],
  );
  const characters = useResource(charactersLoader);
  // After a future check-in succeeds, call characters.retry() independently of members.retry().
  const canDelegate =
    owner &&
    (challenge.status === "READY" || challenge.status === "ACTIVE") &&
    !!members.data?.some((member) => member.userId !== user?.id);
  return (
    <>
      <header className="relative flex h-12 items-center justify-between gap-3">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="뒤로 가기"
          className="p-2"
        >
          <CaretLeftIcon size={24} />
        </button>
        <p className="min-w-0 flex-1 truncate text-center font-bold">
          {group.data?.group.name ?? `시즌 ${challenge.seqNo}`}
        </p>
        <button
          type="button"
          aria-label="채팅"
          onClick={() => showToast("채팅 기능은 준비중입니다.")}
          className="shrink-0 rounded-lg p-2 hover:bg-purple-50"
        >
          <img src={chatIcon} alt="" className="h-6 w-6 object-contain" />
        </button>
        <button
          type="button"
          aria-label="추가 메뉴"
          aria-expanded={menuOpen}
          aria-controls="challenge-menu"
          onClick={() => setMenuOpen(!menuOpen)}
          className="p-2"
        >
          <DotsThreeIcon size={28} />
        </button>
        {menuOpen && (
          <nav
            id="challenge-menu"
            aria-label="챌린지 추가 메뉴"
            className="absolute top-full right-0 z-20 w-56 space-y-1 rounded-xl border border-purple-200 bg-white p-2 shadow-lg"
            onKeyDown={(event) => {
              if (event.key === "Escape") setMenuOpen(false);
            }}
          >
            {group.data && (
              <Link
                className="block rounded-lg px-3 py-3 text-sm hover:bg-purple-50"
                to={`/challenges/groups/${challenge.groupId}`}
              >
                그룹 정보 · 관리 · 나가기
              </Link>
            )}
            <button
              type="button"
              className="w-full rounded-lg px-3 py-3 text-left text-sm hover:bg-purple-50"
              onClick={() => {
                setShowDetails(!showDetails);
                setMenuOpen(false);
              }}
            >
              챌린지 규칙 · 멤버 보기
            </button>
            {isCurrent && owner && challenge.status === "READY" && (
              <button
                type="button"
                className="w-full rounded-lg px-3 py-3 text-left text-sm hover:bg-purple-50"
                onClick={() => {
                  setShowDetails(true);
                  setEditing(true);
                  setMenuOpen(false);
                }}
              >
                챌린지 설정 수정
              </button>
            )}
          </nav>
        )}
      </header>
      {selection}
      {!showDetails && (
        <ChallengeDashboard
          data={data}
          isCurrent={isCurrent}
          currentKnown={currentKnown}
          name={group.data?.group.name ?? `시즌 ${challenge.seqNo}`}
          mapType={group.data?.group.mapType}
          members={members.data ?? null}
          characters={characters.data ?? null}
          onDelegate={
            canDelegate
              ? (member) =>
                  setAction({
                    type: "delegate",
                    userId: member.userId,
                    nickname: member.nickname,
                  })
              : undefined
          }
        />
      )}
      {!showDetails && (
        <div className="space-y-2">
          {characters.loading && (
            <p role="status" className="text-sm text-gray-500">
              캐릭터 정보를 불러오는 중…
            </p>
          )}
          {characters.error && (
            <p role="alert" className="text-sm text-gray-500">
              캐릭터 정보를 불러오지 못했어요.
            </p>
          )}
          <Button variant="secondary" onClick={characters.retry}>
            캐릭터 정보 새로고침
          </Button>
        </div>
      )}
      {!showDetails && members.loading && (
        <p role="status" className="text-sm text-gray-500">
          멤버를 불러오는 중…
        </p>
      )}
      {!showDetails && members.error && (
        <div className="space-y-2">
          <FormAlert message={members.error} />
          <Button variant="secondary" onClick={members.retry}>
            멤버 다시 불러오기
          </Button>
        </div>
      )}
      {showDetails && (
        <Button variant="secondary" onClick={() => setShowDetails(false)}>
          현황으로 돌아가기
        </Button>
      )}
      {group.error && (
        <div className="space-y-2">
          <FormAlert message={group.error} />
          <Button variant="secondary" onClick={group.retry}>
            그룹 정보 다시 불러오기
          </Button>
        </div>
      )}
      {showDetails && (
        <>
          <ChallengeRulesCard settings={challenge} />
          {isCurrent &&
            owner &&
            challenge.status === "READY" &&
            (editing ? (
              <ChallengeSettingsEditor
                challenge={challenge}
                onSaved={onChanged}
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
          <section className="rounded-2xl border border-purple-200 p-4">
            <h2 className="flex items-center gap-2 font-bold">
              <img
                src={peopleIcon}
                alt=""
                width={20}
                height={20}
                className="shrink-0 object-contain"
              />
              시즌 멤버
            </h2>
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
                      <span className="ml-2 text-xs text-purple-500">
                        그룹장
                      </span>
                    )}
                    {canDelegate && member.userId !== user?.id && (
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
                        그룹장 위임
                      </button>
                    )}
                    {isCurrent &&
                      group.data &&
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
              {isCurrent && <GroupManagement detail={group.data} />}
            </>
          )}
        </>
      )}
      {!showDetails && isCurrent && challenge.status === "ACTIVE" && (
        <ExtensionChoicePanel
          challengeId={challenge.id}
          endDate={challenge.endDate}
          members={members.data}
          currentUserId={user?.id}
          onSaved={members.retry}
        />
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
              await delegateOwner(challenge.id, {
                targetUserId: action.userId,
              });
              showToast("그룹장을 위임했어요.");
            } else {
              await kickGroupMember(challenge.groupId, action.userId);
              showToast("그룹원을 내보냈어요.");
            }
            members.retry();
            characters.retry();
            group.retry();
            onChanged();
          }}
        />
      )}
    </>
  );
}

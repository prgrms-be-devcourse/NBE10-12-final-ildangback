import type { GroupDetailResponse } from "../../group/types";
import {
  CaretDownIcon,
  CaretLeftIcon,
  DotsThreeIcon,
} from "@phosphor-icons/react";
import { useNavigate } from "react-router";
import { ChallengeDashboard } from "../components/ChallengeDashboard";
import type { ReactNode } from "react";
import { useCallback, useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError } from "../../../shared/api/client";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import { ShopIcon } from "../../../shared/ui/icons";
import { TopBar } from "../../../shared/ui/TopBar";
import { getGroup, getGroupChallenges, leaveGroup } from "../../group/api";
import { ConfirmActionDialog } from "../../group/components/ConfirmActionDialog";
import { useResource } from "../../group/hooks/useResource";
import {
  getChallenge,
  getChallengeMembers,
  getChallengeCharacters,
} from "../api";
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
  // 시즌이 하나뿐이면 고를 것이 없어 제목 아래를 비워 둔다.
  const seasonPicker = options.length > 1 && (
    <div className="relative mt-0.5">
      <label className="sr-only" htmlFor="challenge-season">
        시즌 선택
      </label>
      <select
        id="challenge-season"
        value={selectedChallengeId}
        disabled={selectingInitialSeason}
        onChange={(event) => setSelectedChallengeId(Number(event.target.value))}
        className="appearance-none rounded-full bg-purple-50 py-0.5 pr-6 pl-2.5 text-[11px] font-semibold text-purple-700 disabled:opacity-60"
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
      <CaretDownIcon
        size={10}
        weight="bold"
        aria-hidden
        className="pointer-events-none absolute top-1/2 right-2 -translate-y-1/2 text-purple-700"
      />
    </div>
  );
  const seasonStatus = seasons.error && (
    <div className="space-y-2">
      <p role="alert" className="text-xs text-gray-500">
        시즌 목록을 불러오지 못했어요. 다시 시도해주세요.
      </p>
      <Button variant="secondary" onClick={seasons.retry}>
        시즌 목록 다시 불러오기
      </Button>
    </div>
  );
  return detail.data ? (
    <ChallengeContent
      key={selectedChallengeId}
      data={detail.data}
      isCurrent={currentId === selectedChallengeId}
      currentKnown={!!group.data}
      group={group}
      seasonPicker={seasonPicker}
      seasonStatus={seasonStatus}
    />
  ) : (
    <>
      <div className="flex justify-center">{seasonPicker}</div>
      {seasonStatus}
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
  isCurrent,
  currentKnown,
  seasonPicker,
  seasonStatus,
  group,
}: {
  data: ChallengeStatusResponse;
  isCurrent: boolean;
  currentKnown: boolean;
  seasonPicker: ReactNode;
  seasonStatus: ReactNode;
  group: ReturnType<typeof useResource<GroupDetailResponse | null>>;
}) {
  const { challenge } = data;
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);
  const { user } = useAuth();
  const { showToast } = useToast();
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
  const joinedGroup = !!group.data?.members.some(
    (member) => member.userId === user?.id,
  );
  const groupOwner = group.data?.group.ownerId === user?.id;
  return (
    <>
      <header className="relative flex min-h-12 items-center justify-between gap-1">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="뒤로 가기"
          className="shrink-0 p-2"
        >
          <CaretLeftIcon size={24} weight="bold" />
        </button>
        <div className="flex min-w-0 flex-1 flex-col items-center">
          <p className="w-full truncate text-center text-[15px] leading-tight font-bold">
            {group.data?.group.name ?? `시즌 ${challenge.seqNo}`}
          </p>
          {seasonPicker}
        </div>
        <button
          type="button"
          aria-label="그룹 상점"
          onClick={() => showToast("그룹 상점은 준비중입니다.")}
          className="shrink-0 rounded-lg p-2 text-purple-600 hover:bg-purple-50"
        >
          <ShopIcon className="h-6 w-6" />
        </button>
        <button
          type="button"
          aria-label="추가 메뉴"
          aria-expanded={menuOpen}
          aria-controls="challenge-menu"
          onClick={() => setMenuOpen(!menuOpen)}
          className="p-2"
        >
          <DotsThreeIcon size={28} weight="bold" />
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
                챌린지 정보
              </Link>
            )}
            {joinedGroup && (
              <div className="border-t border-purple-100 pt-1">
                <button
                  type="button"
                  disabled={groupOwner}
                  onClick={() => {
                    setLeaving(true);
                    setMenuOpen(false);
                  }}
                  className="w-full rounded-lg px-3 py-3 text-left text-sm text-red-600 hover:bg-red-50 disabled:text-gray-400 disabled:hover:bg-transparent"
                >
                  그룹 나가기
                </button>
                {groupOwner && (
                  <p className="px-3 pb-2 text-xs leading-relaxed text-gray-500">
                    그룹장은 챌린지 정보에서 권한을 위임한 뒤 나갈 수 있어요.
                  </p>
                )}
              </div>
            )}
          </nav>
        )}
      </header>
      {seasonStatus}
      <ChallengeDashboard
        data={data}
        isCurrent={isCurrent}
        currentKnown={currentKnown}
        description={group.data?.group.description}
        mapType={group.data?.group.mapType}
        members={members.data ?? null}
        characters={characters.data ?? null}
        currentUserId={user?.id}
        onExtensionSaved={members.retry}
      />
      {(characters.loading || characters.error) && (
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
        </div>
      )}
      {members.loading && (
        <p role="status" className="text-sm text-gray-500">
          멤버를 불러오는 중…
        </p>
      )}
      {members.error && (
        <div className="space-y-2">
          <FormAlert message={members.error} />
          <Button variant="secondary" onClick={members.retry}>
            멤버 다시 불러오기
          </Button>
        </div>
      )}
      {group.error && (
        <div className="space-y-2">
          <FormAlert message={group.error} />
          <Button variant="secondary" onClick={group.retry}>
            그룹 정보 다시 불러오기
          </Button>
        </div>
      )}
      {leaving && (
        <ConfirmActionDialog
          title="그룹에서 나갈까요?"
          description="나가면 현재 챌린지 참여도 종료됩니다. 참여 이력이 있는 그룹에는 다시 가입할 수 없어요."
          confirmLabel="나가기"
          onClose={() => setLeaving(false)}
          onConfirm={async () => {
            await leaveGroup(challenge.groupId);
            showToast("그룹에서 나왔어요.");
            navigate("/challenges", { replace: true });
          }}
        />
      )}
    </>
  );
}

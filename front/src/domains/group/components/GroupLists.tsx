import sportsIcon from "../../../assets/icons/sports.webp";
import codingIcon from "../../../assets/icons/coding.webp";
import studyIcon from "../../../assets/icons/study.webp";
import peopleIcon from "../../../assets/icons/people.webp";
import { ArrowRightIcon } from "@phosphor-icons/react";
import { useCallback } from "react";
import { Link } from "react-router";
import { Button } from "../../../shared/ui/Button";
import { FormAlert } from "../../../shared/ui/FormAlert";
import {
  ChallengeProgress,
  StatusBadge,
} from "../../challenge/components/ChallengeInfo";
import { frequencyLabel } from "../../challenge/presentation";
import { getMyGroups, getPublicGroups } from "../api";
import { CATEGORY_LABEL, categoryMap } from "../constants";
import { useCursorList } from "../hooks/useCursorList";
import type {
  GroupCategory,
  GroupSort,
  GroupStatus,
  MyGroupSummary,
} from "../types";

export function ExploreGroupList({
  keyword,
  category,
  sort,
}: {
  keyword: string;
  category?: GroupCategory;
  sort: GroupSort;
}) {
  const loader = useCallback(
    (cursor?: number) => getPublicGroups({ keyword, category, sort }, cursor),
    [keyword, category, sort],
  );
  const list = useCursorList(loader);
  return (
    <div className="space-y-4">
      {list.page?.content.map((group) => {
        const categoryImage =
          categoryMap(group.category) === "GYM"
            ? sportsIcon
            : group.category === "DEV"
              ? codingIcon
              : studyIcon;
        return (
          <Link
            key={group.id}
            to={`/challenges/groups/${group.id}`}
            className="flex gap-4 rounded-2xl border border-purple-200 p-4 transition-colors hover:bg-purple-50 focus-visible:outline-purple-500"
          >
            <div className="flex h-20 w-20 shrink-0 items-center justify-center self-center rounded-xl bg-purple-50 text-purple-500">
              <img
                src={categoryImage}
                alt=""
                width={42}
                height={42}
                className="shrink-0 object-contain"
              />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h2 className="text-lg font-bold wrap-anywhere">
                  {group.name}
                </h2>
                <span className="text-[11px] font-semibold text-purple-500">
                  {group.currentMembers >= group.maxMembers
                    ? "정원 마감"
                    : "모집 중"}
                </span>
              </div>
              <p className="mt-1 line-clamp-2 text-[13px] text-gray-500 wrap-anywhere">
                {group.description}
              </p>
              <div className="mt-3 space-y-1 border-t border-purple-200 pt-2 text-[12px]">
                <p>
                  {frequencyLabel({ ...group, daysOfWeek: group.weekdays })} ·
                  하루 {group.dailyCheckInCount}회
                </p>
                <p>{group.startDate} 시작</p>
                <div className="flex items-center justify-between">
                  <span className="flex items-center gap-1">
                    <img
                      src={peopleIcon}
                      alt=""
                      width={16}
                      height={16}
                      className="shrink-0 object-contain"
                    />
                    {group.currentMembers} / {group.maxMembers}명
                  </span>
                  <ArrowRightIcon
                    size={22}
                    className="rounded-full bg-purple-500 p-1 text-white"
                  />
                </div>
              </div>
            </div>
          </Link>
        );
      })}
      <ListFeedback {...list} empty="조건에 맞는 그룹이 없어요." />
    </div>
  );
}
export function MyGroupList({ status }: { status?: GroupStatus }) {
  const loader = useCallback(
    (cursor?: number) => getMyGroups(cursor, { status }),
    [status],
  );
  const list = useCursorList(loader);
  const current =
    list.page?.content.filter((group) => group.challengeStatus !== "ENDED") ??
    [];
  const ended =
    list.page?.content.filter((group) => group.challengeStatus === "ENDED") ??
    [];
  return (
    <div className="space-y-5">
      {current.map((group) => (
        <MyCard key={group.challengeId} group={group} />
      ))}
      {ended.length > 0 && (
        <>
          <h2 className="flex items-center gap-3 pt-4 text-sm text-gray-500">
            <span className="h-px flex-1 bg-purple-200" />
            이전 챌린지
          </h2>
          {ended.map((group) => (
            <MyCard key={group.challengeId} group={group} />
          ))}
        </>
      )}
      <ListFeedback
        {...list}
        empty="아직 참여한 그룹이 없어요. 그룹 탐색에서 함께할 그룹을 찾아보세요."
      />
    </div>
  );
}
function MyCard({ group }: { group: MyGroupSummary }) {
  return (
    <Link
      to={`/challenges/${group.challengeId}`}
      className="block space-y-4 rounded-2xl border border-purple-200 p-5 hover:bg-purple-50"
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h2 className="text-xl font-bold wrap-anywhere">{group.name}</h2>
          <p className="mt-1 text-xs text-purple-500">
            {CATEGORY_LABEL[group.category]}
          </p>
        </div>
        <StatusBadge status={group.challengeStatus} />
      </div>
      <ChallengeProgress {...group} />
      <p className="flex items-center justify-between text-sm text-gray-500">
        <span className="flex items-center gap-1">
          <img
            src={peopleIcon}
            alt=""
            width={16}
            height={16}
            className="shrink-0 object-contain"
          />
          {group.participantCount}명 참여
        </span>
        <ArrowRightIcon size={20} />
      </p>
    </Link>
  );
}
function ListFeedback({
  page,
  loading,
  error,
  retry,
  loadMore,
  empty,
}: {
  page: { content: unknown[]; hasNext: boolean } | null;
  loading: boolean;
  error: string | null;
  retry: () => void;
  loadMore: () => void;
  empty: string;
}) {
  return (
    <>
      {loading && (
        <p role="status" className="py-8 text-center text-sm text-gray-500">
          그룹을 불러오는 중…
        </p>
      )}
      {error && (
        <div className="space-y-3">
          <FormAlert message={error} />
          <Button variant="secondary" onClick={retry} loading={loading}>
            다시 시도
          </Button>
        </div>
      )}
      {!loading && !error && page?.content.length === 0 && (
        <p className="rounded-2xl bg-purple-50 px-5 py-12 text-center text-sm leading-relaxed text-gray-500">
          {empty}
        </p>
      )}
      {!loading && !error && page?.hasNext && (
        <Button variant="secondary" onClick={loadMore}>
          더 보기
        </Button>
      )}
    </>
  );
}

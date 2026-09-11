import filterIcon from "../../../assets/icons/mingcute_filter_line.webp";
import searchIcon from "../../../assets/icons/tdesign_search.webp";
import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { ChallengeTabPlaceholder } from "../../../pages/ChallengeTabPlaceholder";
import { useAuth } from "../../../shared/lib/useAuth";
import { CategoryFilterSheet } from "../components/CategoryFilterSheet";
import { ExploreGroupList, MyGroupList } from "../components/GroupLists";
import { CATEGORIES, CATEGORY_LABEL } from "../constants";
import type { GroupCategory, GroupSort, GroupStatus } from "../types";

export function GroupHomePage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const [filterOpen, setFilterOpen] = useState(false);
  const explore = params.get("tab") === "explore";
  const keyword = params.get("keyword") ?? "";
  const rawCategory = params.get("category");
  const category = CATEGORIES.includes(rawCategory as GroupCategory)
    ? (rawCategory as GroupCategory)
    : undefined;
  const rawSort = params.get("sort");
  const rawStatus = params.get("status");
  const status: GroupStatus | undefined =
    rawStatus === "READY" || rawStatus === "ACTIVE" || rawStatus === "ENDED"
      ? rawStatus
      : undefined;
  const sort: GroupSort =
    rawSort === "POPULAR" || rawSort === "START_SOON" ? rawSort : "LATEST";
  const update = (key: string, value?: string) => {
    // BrowserRouter updates history before a transition finishes rendering.
    // Preserve the latest URL even when two controls are used back-to-back.
    const next = new URLSearchParams(window.location.search);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next);
  };
  if (!user) return <ChallengeTabPlaceholder />;
  return (
    <div className="px-5 pt-6 pb-10">
      <header className="mb-6 flex items-center justify-between">
        <h1 className="text-[28px] font-bold">챌린지</h1>
        <Link
          to="/challenges/groups/new"
          className="rounded-lg bg-purple-500 px-4 py-2.5 text-sm font-semibold text-white"
        >
          그룹 만들기
        </Link>
      </header>
      <nav aria-label="그룹 목록" className="mb-6 grid grid-cols-2 gap-2">
        {[
          { name: "내 그룹", active: !explore, value: undefined },
          { name: "그룹 탐색하기", active: explore, value: "explore" },
        ].map((tab) => (
          <button
            key={tab.name}
            type="button"
            aria-current={tab.active ? "page" : undefined}
            onClick={() => update("tab", tab.value)}
            className={`rounded-full py-3 text-sm font-semibold ${tab.active ? "bg-purple-500 text-white" : "text-gray-500"}`}
          >
            {tab.name}
          </button>
        ))}
      </nav>
      {explore ? (
        <>
          <SearchForm
            keyword={keyword}
            onSearch={(value) => update("keyword", value)}
          />
          <div className="my-4 flex items-center justify-between">
            <span className="rounded-full bg-purple-500 px-3 py-1.5 text-xs text-white">
              {category ? CATEGORY_LABEL[category] : "전체"}
            </span>
            <button
              type="button"
              aria-label="카테고리 필터"
              onClick={() => setFilterOpen(true)}
              className="p-2 text-purple-700"
            >
              <img
                src={filterIcon}
                alt=""
                width={24}
                height={24}
                className="shrink-0 object-contain"
              />
            </button>
          </div>
          <div className="mb-4 flex justify-end">
            <label className="sr-only" htmlFor="group-sort">
              그룹 정렬
            </label>
            <select
              id="group-sort"
              value={sort}
              onChange={(e) => update("sort", e.target.value)}
              className="rounded-lg bg-white px-2 py-2 text-gray-500"
            >
              <option value="LATEST">최신순</option>
              <option value="START_SOON">시작 임박순</option>
              <option value="POPULAR">참여 인원순</option>
            </select>
          </div>
          <Link
            to="/challenges/groups/join"
            className="mb-5 block rounded-xl bg-purple-50 p-4 text-sm font-semibold text-purple-700"
          >
            초대코드로 비공개 그룹 참여 →
          </Link>
          <ExploreGroupList
            key={JSON.stringify([user.id, keyword, category, sort])}
            keyword={keyword}
            category={category}
            sort={sort}
          />
        </>
      ) : (
        <>
          <label className="mb-4 flex items-center justify-between text-sm text-gray-500">
            그룹 상태
            <select
              aria-label="내 그룹 상태"
              value={status ?? ""}
              onChange={(event) => update("status", event.target.value)}
              className="rounded-lg bg-white p-2"
            >
              <option value="">전체</option>
              <option value="READY">시작 전</option>
              <option value="ACTIVE">진행 중</option>
              <option value="ENDED">종료</option>
            </select>
          </label>
          <MyGroupList key={`${user.id}:${status ?? "all"}`} status={status} />
        </>
      )}
      {filterOpen && (
        <CategoryFilterSheet
          value={category}
          onApply={(value) => update("category", value)}
          onClose={() => setFilterOpen(false)}
        />
      )}
    </div>
  );
}
function SearchForm({
  keyword,
  onSearch,
}: {
  keyword: string;
  onSearch: (value: string) => void;
}) {
  const [draft, setDraft] = useState({ keyword, value: keyword });
  if (draft.keyword !== keyword) {
    // Restore back/forward searches without overwriting text entered while a
    // previous search transition was still committing.
    setDraft({
      keyword,
      value: draft.value === draft.keyword ? keyword : draft.value,
    });
  }
  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        onSearch(draft.value.trim());
      }}
      className="flex items-center rounded-xl border border-purple-200 px-4"
    >
      <input
        aria-label="그룹명 검색"
        placeholder="그룹명을 검색해보세요"
        value={draft.value}
        onChange={(e) => setDraft({ keyword, value: e.target.value })}
        className="h-13 min-w-0 flex-1 bg-transparent outline-none"
      />
      <button type="submit" aria-label="검색" className="p-2 text-gray-500">
        <img
          src={searchIcon}
          alt=""
          width={24}
          height={24}
          className="shrink-0 object-contain"
        />
      </button>
    </form>
  );
}

import {
  CaretDownIcon,
  CaretRightIcon,
  MagnifyingGlassIcon,
} from "@phosphor-icons/react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { getMyChallengeMergeOverviews } from "../api";
import { MergeProgressDots } from "../components/MergeProgressDots";
import { CATEGORY_LABEL } from "../lib/category";
import { TopBar } from "../../../shared/ui/TopBar";
import type {
  ChallengeMergeOverviewResponse,
  GroupCategory,
} from "../../../shared/api/types";

/** LocalDate("YYYY-MM-DD")를 "YYYY.MM.DD"로. */
function formatDateDot(localDate: string): string {
  return localDate.replaceAll("-", ".");
}

const ALL_CATEGORIES = "ALL";

export function MyMonthlyMergeArchivePage() {
  const navigate = useNavigate();
  const [overviews, setOverviews] = useState<
    ChallengeMergeOverviewResponse[] | null
  >(null);
  const [error, setError] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState<
    GroupCategory | typeof ALL_CATEGORIES
  >(ALL_CATEGORIES);

  useEffect(() => {
    let cancelled = false;

    getMyChallengeMergeOverviews()
      .then((list) => {
        if (!cancelled) setOverviews(list);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = (overviews ?? []).filter((o) => {
    if (category !== ALL_CATEGORIES && o.category !== category) return false;
    if (keyword && !o.groupName.includes(keyword)) return false;
    return true;
  });
  const inProgress = filtered.filter((o) => !o.hasFinalMerge);
  const finished = filtered.filter((o) => o.hasFinalMerge);

  return (
    <>
      <TopBar title="월간 머지 아카이브" />

      <div className="px-5 pt-4 pb-10">
        <div className="flex items-center gap-2 rounded-xl border border-purple-200 bg-white px-4 py-3.5">
          <MagnifyingGlassIcon
            size={18}
            className="text-gray-400"
            aria-hidden
          />
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="챌린지 검색"
            className="w-full text-[14px] text-gray-900 placeholder:text-gray-400 focus:outline-none"
          />
        </div>

        <CategorySelect value={category} onChange={setCategory} />

        {error && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            아카이브를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
          </p>
        )}

        {!error && !overviews && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            불러오는 중…
          </p>
        )}

        {!error && overviews && filtered.length === 0 && (
          <p className="py-10 text-center text-[13px] text-gray-500">
            {overviews.length === 0
              ? "아직 참여 중인 챌린지가 없어요."
              : "조건에 맞는 챌린지가 없어요."}
          </p>
        )}

        {inProgress.length > 0 && (
          <section className="mt-6">
            <h2 className="mb-3 text-[15px] font-bold text-gray-900">
              진행 중인 챌린지
            </h2>
            <div className="space-y-3">
              {inProgress.map((overview) => (
                <ChallengeOverviewCard
                  key={overview.challengeId}
                  overview={overview}
                  onClick={() =>
                    navigate(`/challenges/${overview.challengeId}/merges`)
                  }
                />
              ))}
            </div>
          </section>
        )}

        {finished.length > 0 && (
          <section className="mt-6">
            <h2 className="mb-3 text-[15px] font-bold text-gray-900">
              지난 챌린지
            </h2>
            <div className="space-y-3">
              {finished.map((overview) => (
                <button
                  key={overview.challengeId}
                  type="button"
                  onClick={() =>
                    navigate(`/challenges/${overview.challengeId}/merges`)
                  }
                  className="flex w-full items-center justify-between rounded-2xl border border-purple-200 bg-white px-4 py-4 text-left transition-colors hover:bg-purple-50/50"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="text-[14px] font-bold text-gray-900">
                        {overview.groupName}
                      </p>
                      <span className="rounded-full bg-purple-50 px-2 py-0.5 text-[11px] font-medium text-purple-600">
                        {CATEGORY_LABEL[overview.category]}
                      </span>
                    </div>
                    <p className="mt-0.5 text-[12px] text-gray-500">
                      {overview.totalDays}일 ·{" "}
                      {formatDateDot(overview.periodStart)} -{" "}
                      {formatDateDot(overview.periodEnd)}
                    </p>
                  </div>
                  <div className="flex items-center gap-1 text-[13px] text-gray-500">
                    월간머지{" "}
                    <span className="font-bold text-purple-600">
                      {overview.completedMergeCount}
                    </span>
                    개
                    <CaretRightIcon
                      size={16}
                      className="ml-1 text-gray-300"
                      aria-hidden
                    />
                  </div>
                </button>
              ))}
            </div>
          </section>
        )}
      </div>
    </>
  );
}

function CategorySelect({
  value,
  onChange,
}: {
  value: GroupCategory | typeof ALL_CATEGORIES;
  onChange(value: GroupCategory | typeof ALL_CATEGORIES): void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onPointerDown);
    return () => document.removeEventListener("pointerdown", onPointerDown);
  }, [open]);

  const label =
    value === ALL_CATEGORIES ? "전체 카테고리" : CATEGORY_LABEL[value];

  return (
    <div ref={rootRef} className="relative mt-3 inline-block">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-1.5 rounded-lg border border-purple-200 bg-white px-4 py-2 text-[13px] font-medium text-gray-600"
      >
        {label}
        <CaretDownIcon
          size={14}
          weight="light"
          className={`transition-transform ${open ? "rotate-180" : ""}`}
          aria-hidden
        />
      </button>

      {open && (
        <ul className="absolute top-full left-0 z-10 mt-1.5 w-max min-w-full overflow-hidden rounded-[10px] border border-[#D9CDED] bg-white py-1 shadow-[0_8px_20px_rgba(0,0,0,0.08)]">
          <li>
            <button
              type="button"
              onClick={() => {
                onChange(ALL_CATEGORIES);
                setOpen(false);
              }}
              className={`w-full px-4 py-2.5 text-left text-[14px] whitespace-nowrap ${
                value === ALL_CATEGORIES
                  ? "font-semibold text-[#784CBE]"
                  : "text-[#4A4A4A] hover:bg-purple-50"
              }`}
            >
              전체 카테고리
            </button>
          </li>
          {(Object.entries(CATEGORY_LABEL) as [GroupCategory, string][]).map(
            ([key, text]) => (
              <li key={key}>
                <button
                  type="button"
                  onClick={() => {
                    onChange(key);
                    setOpen(false);
                  }}
                  className={`w-full px-4 py-2.5 text-left text-[14px] whitespace-nowrap ${
                    key === value
                      ? "font-semibold text-[#784CBE]"
                      : "text-[#4A4A4A] hover:bg-purple-50"
                  }`}
                >
                  {text}
                </button>
              </li>
            ),
          )}
        </ul>
      )}
    </div>
  );
}

function ChallengeOverviewCard({
  overview,
  onClick,
}: {
  overview: ChallengeMergeOverviewResponse;
  onClick(): void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="block w-full rounded-[7px] border-[0.7px] border-[#D9CDED] bg-[#FEFEFE] p-4 text-left transition-colors hover:bg-purple-50/50"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <p className="text-[14px] font-bold text-black">
            {overview.groupName}
          </p>
          <span className="rounded-[7px] bg-[#F4F1FB] px-2 py-0.5 text-[10px] font-medium text-[#8058C4]">
            {CATEGORY_LABEL[overview.category]}
          </span>
        </div>
        <CaretRightIcon size={18} className="text-gray-300" aria-hidden />
      </div>
      <p className="mt-0.5 text-[11px] text-[#7A7697]">
        {overview.totalDays}일 챌린지 · {formatDateDot(overview.periodStart)} -{" "}
        {formatDateDot(overview.periodEnd)}
      </p>

      <div className="mt-4 flex items-center gap-1.5 text-[14px] font-semibold text-black">
        월간 머지
        <span className="text-[20px] font-semibold text-[#794CC7]">
          {overview.completedMergeCount}
        </span>
        <span className="text-black">/ {overview.totalMergeCount}개</span>
      </div>

      <div className="mt-4">
        <MergeProgressDots
          totalCount={overview.totalMergeCount}
          completedCount={overview.completedMergeCount}
          hasCurrentCycle={overview.currentSeqNo != null}
        />
      </div>

      {overview.currentSeqNo != null && (
        <p className="mt-3 rounded-[7px] bg-[#F4F1FB] px-4 py-2 text-[11px] text-[#515151]">
          현재{" "}
          <span className="font-semibold text-[#794CC7]">
            #{String(overview.currentSeqNo).padStart(2, "0")}
          </span>{" "}
          · DAY{" "}
          <span className="font-semibold text-[#794CC7]">
            {overview.currentCycleDay}
          </span>
          /{overview.cycleLengthDays}
        </p>
      )}
    </button>
  );
}

import { useEffect, useRef } from "react";
import { formatMonthDay } from "../../../shared/lib/date";
import type { CheckIn } from "../types";
import { CheckInCell } from "./CheckInCell";

interface Props {
  items: CheckIn[];
  /** true 면 businessDate 별로 "8월 25일" 헤더를 붙여 그룹. 갤러리 탭은 false(평평). */
  groupByDate?: boolean;
  showAuthor?: boolean;
  onSelect: (item: CheckIn) => void;
  hasNext: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;
}

/** 인증 3열 그리드 + 커서 무한스크롤. 갤러리 탭 · 프로필 모아보기가 공유. */
export function CheckInGrid({
  items,
  groupByDate = false,
  showAuthor = false,
  onSelect,
  hasNext,
  loadingMore,
  onLoadMore,
}: Props) {
  const sentinelRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = sentinelRef.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) onLoadMore();
      },
      { rootMargin: "240px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [onLoadMore]);

  return (
    <>
      {groupByDate ? (
        groupByBusinessDate(items).map(([date, group]) => (
          <section key={date} className="mt-5 first:mt-0">
            <h3 className="mb-2 text-[13px] font-bold text-gray-900">
              {formatMonthDay(date)}
            </h3>
            <Cells items={group} showAuthor={showAuthor} onSelect={onSelect} />
          </section>
        ))
      ) : (
        <Cells items={items} showAuthor={showAuthor} onSelect={onSelect} />
      )}

      {hasNext && <div ref={sentinelRef} className="h-1" />}
      {loadingMore && (
        <div className="flex justify-center py-4">
          <span
            role="status"
            aria-label="더 불러오는 중"
            className="size-6 animate-spin rounded-full border-3 border-purple-200 border-t-purple-500"
          />
        </div>
      )}
    </>
  );
}

function Cells({
  items,
  showAuthor,
  onSelect,
}: {
  items: CheckIn[];
  showAuthor: boolean;
  onSelect: (item: CheckIn) => void;
}) {
  return (
    <div className="grid grid-cols-3 gap-1">
      {items.map((item) => (
        <CheckInCell
          key={item.id}
          item={item}
          showAuthor={showAuthor}
          onSelect={() => onSelect(item)}
        />
      ))}
    </div>
  );
}

/** 응답이 이미 최신순이라 순서를 유지하며 businessDate 로 묶기만 한다. */
function groupByBusinessDate(items: CheckIn[]): [string, CheckIn[]][] {
  const groups: [string, CheckIn[]][] = [];
  for (const item of items) {
    const last = groups.at(-1);
    if (last && last[0] === item.businessDate) last[1].push(item);
    else groups.push([item.businessDate, [item]]);
  }
  return groups;
}

export function CheckInGridSkeleton() {
  return (
    <div className="grid grid-cols-3 gap-1">
      {Array.from({ length: 9 }, (_, i) => (
        <div
          key={i}
          className="aspect-square animate-pulse rounded-md bg-gray-100"
        />
      ))}
    </div>
  );
}

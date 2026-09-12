import { Button } from "../../../shared/ui/Button";
import type { CursorPageResult } from "../lib/useCursorPage";
import type { CheckIn, CursorPageMeta } from "../types";
import { CheckInGrid, CheckInGridSkeleton } from "./CheckInGrid";

interface Props<T extends CheckIn> {
  result: CursorPageResult<T, CursorPageMeta>;
  emptyMessage: string;
  onSelect: (item: T) => void;
  groupByDate?: boolean;
  showAuthor?: boolean;
}

/**
 * 커서 페이지 결과를 받아 로딩(스켈레톤) / 에러 / 빈 상태 / 그리드를 렌더한다.
 * 갤러리 탭·프로필 모아보기가 공유.
 */
export function CheckInGridSection<T extends CheckIn>({
  result,
  emptyMessage,
  onSelect,
  groupByDate = false,
  showAuthor = false,
}: Props<T>) {
  const { items, loading, loadingMore, error, hasNext, loadMore, reload } =
    result;

  if (loading) return <CheckInGridSkeleton />;

  if (error) {
    return (
      <div className="py-16 text-center">
        <p className="text-[14px] text-gray-500">불러오지 못했어요</p>
        <Button variant="secondary" className="mt-4" onClick={reload}>
          다시 시도
        </Button>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <p className="py-16 text-center text-[14px] text-gray-400">
        {emptyMessage}
      </p>
    );
  }

  return (
    <CheckInGrid
      items={items}
      groupByDate={groupByDate}
      showAuthor={showAuthor}
      onSelect={onSelect}
      hasNext={hasNext}
      loadingMore={loadingMore}
      onLoadMore={loadMore}
    />
  );
}

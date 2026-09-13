import { useSentinel } from "../lib/useSentinel";

interface Props {
  hasNext: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;
}

/** 리스트 끝의 무한스크롤 트리거 + "더 불러오는 중" 스피너. */
export function LoadMore({ hasNext, loadingMore, onLoadMore }: Props) {
  const ref = useSentinel(onLoadMore);
  return (
    <>
      {hasNext && <div ref={ref} className="h-1" />}
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

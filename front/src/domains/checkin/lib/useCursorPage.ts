import { useCallback, useEffect, useRef, useState } from "react";
import type { CursorPageMeta } from "../types";

interface Page<T, M> {
  /** 이 페이지가 어느 요청(deps)의 것인지. deps 가 바뀌면 옛 응답을 버리는 데 쓴다. */
  key: string;
  items: T[];
  meta: M;
}

export interface CursorPageResult<T, M> {
  items: T[];
  /** 현재 deps 에 해당하는 페이지의 meta. 헤더 집계(totalCount 등)에 쓴다. */
  meta: M | null;
  loading: boolean;
  loadingMore: boolean;
  error: boolean;
  hasNext: boolean;
  loadMore: () => void;
  reload: () => void;
}

/**
 * 커서 기반 무한스크롤 공통 상태기. 갤러리 탭 · 프로필 모아보기 · 일일 로그가 공유한다.
 *
 * `deps` 가 바뀌면(필터 변경 등) 첫 페이지부터 다시 받는다. 로딩/에러는 "지금 deps 에
 * 해당하는 데이터가 있는가" 로 파생하므로, 필터 전환 중 도착한 이전 요청의 응답은
 * 자연히 무시된다. `fetchPage` 는 매 렌더 새로 만들어도 된다(ref 로 최신값 유지) —
 * 재요청 트리거는 오직 `deps` 다.
 */
export function useCursorPage<T, M extends CursorPageMeta = CursorPageMeta>(
  fetchPage: (cursor: number | undefined) => Promise<{ content: T[]; meta: M }>,
  deps: readonly unknown[],
): CursorPageResult<T, M> {
  const [reloadKey, setReloadKey] = useState(0);
  const [page, setPage] = useState<Page<T, M> | null>(null);
  const [errorKey, setErrorKey] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  const requestKey = JSON.stringify([deps, reloadKey]);

  const fetchRef = useRef(fetchPage);
  useEffect(() => {
    fetchRef.current = fetchPage;
  });

  useEffect(() => {
    let cancelled = false;
    fetchRef
      .current(undefined)
      .then((res) => {
        if (!cancelled) {
          setPage({ key: requestKey, items: res.content, meta: res.meta });
        }
      })
      .catch(() => {
        if (!cancelled) setErrorKey(requestKey);
      });
    return () => {
      cancelled = true;
    };
  }, [requestKey]);

  const fresh = page?.key === requestKey ? page : null;
  const error = errorKey === requestKey;
  const loading = !fresh && !error;

  const loadMore = useCallback(() => {
    if (
      loading ||
      loadingMore ||
      !fresh?.meta.hasNext ||
      fresh.meta.nextCursor == null
    ) {
      return;
    }
    const keyAtCall = requestKey;
    setLoadingMore(true);
    fetchRef
      .current(fresh.meta.nextCursor)
      .then((res) => {
        setLoadingMore(false);
        setPage((prev) =>
          prev && prev.key === keyAtCall
            ? {
                ...prev,
                items: [...prev.items, ...res.content],
                meta: res.meta,
              }
            : prev,
        );
      })
      .catch(() => setLoadingMore(false));
  }, [loading, loadingMore, fresh, requestKey]);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  return {
    items: fresh?.items ?? [],
    meta: fresh?.meta ?? null,
    loading,
    loadingMore,
    error,
    hasNext: fresh?.meta.hasNext ?? false,
    loadMore,
    reload,
  };
}

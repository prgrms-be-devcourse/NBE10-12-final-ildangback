import { useEffect, useRef, useState } from "react";
import { groupErrorMessage } from "../errors";
import type { SliceResponse } from "../types";

export function useCursorList<T>(
  loader: (cursor?: number) => Promise<SliceResponse<T>>,
) {
  const [page, setPage] = useState<SliceResponse<T> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const alive = useRef(false);
  const busy = useRef(false);
  const request = async (cursor?: number) => {
    if (busy.current) return;
    busy.current = true;
    setLoading(true);
    setError(null);
    try {
      const next = await loader(cursor);
      if (alive.current)
        setPage((prev) => ({
          ...next,
          content:
            cursor === undefined
              ? next.content
              : [...(prev?.content ?? []), ...next.content],
        }));
    } catch (err) {
      if (alive.current) setError(groupErrorMessage(err));
    } finally {
      busy.current = false;
      if (alive.current) setLoading(false);
    }
  };
  useEffect(() => {
    alive.current = true;
    let cancelled = false;
    loader()
      .then((next) => {
        if (!cancelled) {
          setPage(next);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(groupErrorMessage(err));
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
      alive.current = false;
    };
  }, [loader]);
  // Callers key each list by its complete query so a new search owns a fresh cursor and state.
  return {
    page,
    loading,
    error,
    loadMore: () => {
      if (page?.hasNext && page.nextCursor !== null)
        void request(page.nextCursor);
    },
    retry: () => void request(page?.nextCursor ?? undefined),
  };
}

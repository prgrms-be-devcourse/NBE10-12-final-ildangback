import { useEffect, useRef, useState } from "react";

/**
 * deps 가 바뀔 때마다 한 번 fetch 해서 값을 보여주는 공통 훅. 실패하면 `fallback`
 * 으로 되돌아간다 — 화면을 막지 않고 빈 상태로 대체하는 참여자 목록 · 내 챌린지
 * 목록 · 앨범 요약이 공유한다. (무한스크롤은 `useCursorPage` 참고)
 */
export function useFetchOnce<T>(
  fetchValue: () => Promise<T>,
  deps: readonly unknown[],
  fallback: T,
): T {
  const [value, setValue] = useState<T>(fallback);
  const depsKey = JSON.stringify(deps);

  const fetchRef = useRef(fetchValue);
  useEffect(() => {
    fetchRef.current = fetchValue;
  });

  useEffect(() => {
    let cancelled = false;
    fetchRef
      .current()
      .then((next) => {
        if (!cancelled) setValue(next);
      })
      .catch(() => {
        if (!cancelled) setValue(fallback);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- depsKey 로 충분
  }, [depsKey]);

  return value;
}

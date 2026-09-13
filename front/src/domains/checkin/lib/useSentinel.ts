import { useEffect, useRef } from "react";

/**
 * 무한스크롤 sentinel. 반환한 ref 를 리스트 끝 요소에 달면, 그 요소가 화면 근처에
 * 들어올 때 `onIntersect` 를 부른다.
 */
export function useSentinel(onIntersect: () => void) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) onIntersect();
      },
      { rootMargin: "240px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [onIntersect]);

  return ref;
}

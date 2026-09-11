import { useEffect, useRef } from "react";

/**
 * 화면에 보이는 동안만 `<video>` 를 재생한다. 리스트에 autoPlay 비디오가 여러 개면
 * 안 보이는 것까지 디코딩·재생해 저사양 모바일에서 렌더 지연·배터리 소모가 크다.
 * 반환한 ref 를 video 요소에 달고 autoPlay 속성은 빼면 된다.
 */
export function useAutoPlayInView<
  T extends HTMLVideoElement = HTMLVideoElement,
>() {
  const ref = useRef<T>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          // 자동재생 정책 거부 등은 무시한다(muted+playsInline 이면 대개 성공).
          void el.play().catch(() => {});
        } else {
          el.pause();
        }
      },
      { threshold: 0.25 },
    );
    io.observe(el);
    return () => io.disconnect();
  }, []);

  return ref;
}

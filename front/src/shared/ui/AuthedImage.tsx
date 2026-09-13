import { useEffect, useRef, useState, type ImgHTMLAttributes } from "react";
import { useAuthedImage } from "../lib/useAuthedImage";

// 로딩 전 자리를 차지할 1x1 투명 GIF. src 없는 <img> 가 브라우저별로 깨진 아이콘을
// 띄우는 걸 막는다. 레이아웃 크기는 className / 부모(aspect-square 등)가 잡는다.
const TRANSPARENT_PX =
  "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///ywAAAAAAQABAAACAUwAOw==";

interface Props extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src"> {
  src: string | null | undefined;
  /**
   * 뷰포트 근처에 올 때까지 fetch 를 미룬다. 그리드 셀처럼 한 번에 많이 그릴 때 켠다.
   * 라이트박스처럼 한 장만 즉시 보여줄 땐 false. 기본 true.
   */
  lazy?: boolean;
}

/**
 * 인증이 필요한 미디어(`/api/check-ins/{id}/media` 등)를 그리는 `<img>` 대체 컴포넌트.
 *
 * 브라우저 `<img src>` 요청엔 `Authorization` 헤더가 안 실려 인증 미디어가 401 로 깨진다.
 * 여기서는 fetch 로 받아(useAuthedImage) objectURL 로 바꿔 그린다. 네트워크 요청 수는
 * 그대로(엔드포인트당 GET 1회) — 헤더가 붙는다는 것만 다르다.
 *
 * 외부 절대 URL(dev 스텁 등)은 fetch 없이 그대로 통과시킨다.
 */
export function AuthedImage({
  src,
  lazy = true,
  alt = "",
  ...imgProps
}: Props) {
  const ref = useRef<HTMLImageElement>(null);
  // IntersectionObserver 가 없는 환경(테스트 · 구형)에서는 lazy 를 포기하고 바로 로드한다.
  const [near, setNear] = useState(
    () => !lazy || typeof IntersectionObserver === "undefined",
  );

  useEffect(() => {
    if (near) return;
    const el = ref.current;
    if (!el) return;

    const io = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) {
          setNear(true);
          io.disconnect();
        }
      },
      { rootMargin: "300px" },
    );
    io.observe(el);
    return () => io.disconnect();
  }, [near]);

  const { url } = useAuthedImage(src, near);

  return <img ref={ref} src={url ?? TRANSPARENT_PX} alt={alt} {...imgProps} />;
}

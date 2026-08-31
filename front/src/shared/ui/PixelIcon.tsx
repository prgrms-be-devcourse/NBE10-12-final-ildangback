/**
 * 픽셀아트 아이콘. 원본은 408×408 webp (docs/export_... 산출물)다.
 *
 * image-rendering: pixelated 를 쓰지 않는다. 408px 를 28px 로 줄이면 14배 축소인데
 * 최근접 방식은 그 배율에서 아트 행을 통째로 건너뛰어 오히려 뭉갠다.
 * 원본이 충분히 크므로 브라우저 기본 축소가 더 깨끗하다 — 나란히 놓고 확인했다.
 *
 * UI 컨트롤(캐럿 · 눈 · 체크)까지 픽셀아트로 바꾸지는 않는다. 그건 phosphor 가 맡는다.
 * 여기 쓰는 것은 메뉴 · 섹션처럼 "무엇에 대한 항목인지" 를 가리키는 아이콘이다.
 */
interface PixelIconProps {
  src: string;
  /** 장식용이면 생략한다. 옆에 같은 뜻의 글자가 있으면 대개 장식이다. */
  alt?: string;
  size?: number;
  className?: string;
}

export function PixelIcon({
  src,
  alt = "",
  size = 28,
  className = "",
}: PixelIconProps) {
  return (
    <img
      src={src}
      alt={alt}
      aria-hidden={alt === "" ? true : undefined}
      width={size}
      height={size}
      loading="lazy"
      decoding="async"
      className={`shrink-0 ${className}`}
      style={{ width: size, height: size }}
    />
  );
}

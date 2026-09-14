import type { VideoHTMLAttributes } from "react";
import { useAuthedImage } from "../lib/useAuthedImage";

interface Props extends Omit<VideoHTMLAttributes<HTMLVideoElement>, "src"> {
  src: string | null | undefined;
}

/**
 * 인증이 필요한 영상 미디어(`/api/check-ins/{id}/media`)를 그리는 `<video>` 대체 컴포넌트.
 * `AuthedImage` 와 같은 이유로 필요하다 — `<video src>` 도 `Authorization` 헤더를 못 실어
 * 401 로 깨진다. `useAuthedImage` 는 이미지/영상을 가리지 않고 그냥 fetch→objectURL 이라
 * 그대로 재사용한다.
 *
 * 라이트박스처럼 한 개만 즉시 보여주는 용도라 lazy 옵션은 없다(AuthedImage 와 달리 그리드에
 * 여러 개를 한꺼번에 그리지 않는다 — 그리드 칸은 영상이 아니라 posterUrl 정지 이미지를 쓴다).
 */
export function AuthedVideo({ src, ...videoProps }: Props) {
  const { url } = useAuthedImage(src);
  if (!url) return null;
  return <video src={url} {...videoProps} />;
}

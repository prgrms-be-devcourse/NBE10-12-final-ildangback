import { useEffect, useState } from "react";
import { apiFetchBlob } from "../api/client";

/**
 * 같은 URL 을 여러 컴포넌트(그리드 셀 + 라이트박스)가 동시에 요청하면 fetch 를 1회로 묶는다.
 * 완료되면 즉시 비운다 — Blob 을 세션 내내 붙들지 않는다(메모리). 추가 네트워크 요청은
 * 없다: `<img src>` 도 어차피 GET 1회를 하고, 그게 401 로 실패할 뿐이다.
 */
const inflight = new Map<string, Promise<Blob>>();

function loadBlob(src: string): Promise<Blob> {
  const shared = inflight.get(src);
  if (shared) return shared;
  const p = apiFetchBlob(src).finally(() => inflight.delete(src));
  inflight.set(src, p);
  return p;
}

export interface AuthedImageState {
  /** `<img src>` 에 넣을 값. 로딩 중이거나 실패면 undefined. */
  url: string | undefined;
  status: "loading" | "loaded" | "error";
}

/**
 * 인증이 필요한 미디어 엔드포인트(`/api/...`)를 fetch 로 받아 objectURL 로 바꾼다.
 * `<img src>` 는 Authorization 헤더를 못 실어 401 이 나기 때문이다.
 *
 * - `src` 가 `/` 로 시작하지 않으면(외부 절대 URL · dev 스텁) 그대로 돌려준다.
 * - `enabled=false` 면 fetch 를 미룬다 — 뷰포트 밖 lazy 로딩용(AuthedImage 가 조절).
 * - objectURL 은 언마운트 · src 변경 시 revoke 한다.
 */
export function useAuthedImage(
  src: string | null | undefined,
  enabled = true,
): AuthedImageState {
  const isExternal = !!src && !src.startsWith("/");
  const [loaded, setLoaded] = useState<{ src: string; url: string } | null>(
    null,
  );
  const [erroredSrc, setErroredSrc] = useState<string | null>(null);

  useEffect(() => {
    if (!src || isExternal || !enabled) return;

    let objectUrl: string | null = null;
    let alive = true;

    loadBlob(src)
      .then((blob) => {
        if (!alive) return;
        objectUrl = URL.createObjectURL(blob);
        setLoaded({ src, url: objectUrl });
      })
      .catch(() => {
        if (alive) setErroredSrc(src);
      });

    return () => {
      alive = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [src, isExternal, enabled]);

  if (isExternal) return { url: src ?? undefined, status: "loaded" };
  if (!src) return { url: undefined, status: "error" };
  if (loaded?.src === src) return { url: loaded.url, status: "loaded" };
  if (erroredSrc === src) return { url: undefined, status: "error" };
  return { url: undefined, status: "loading" };
}

import { useEffect } from "react";

/**
 * 모달·시트가 열려 있는 동안 body 스크롤을 잠근다.
 *
 * 겹쳐 열릴 수 있어서(시트 위 모달 등) 참조 카운트로 관리한다 — 마지막 하나가 닫힐 때만
 * 원래 값으로 되돌린다. 각자 `overflow=""` 로 하드리셋하면 하나 닫힐 때 나머지가 열려 있어도
 * 배경 스크롤이 풀린다.
 */
let lockCount = 0;
let previousOverflow = "";

export function useBodyScrollLock(active: boolean): void {
  useEffect(() => {
    if (!active) return;

    if (lockCount === 0) {
      previousOverflow = document.body.style.overflow;
      document.body.style.overflow = "hidden";
    }
    lockCount += 1;

    return () => {
      lockCount -= 1;
      if (lockCount === 0) {
        document.body.style.overflow = previousOverflow;
      }
    };
  }, [active]);
}

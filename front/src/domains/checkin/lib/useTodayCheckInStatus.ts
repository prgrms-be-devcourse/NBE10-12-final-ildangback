import { useCallback, useEffect, useState } from "react";
import { getTodayCheckInStatus } from "../api";
import type { TodayCheckInStatus } from "../types";

/**
 * GET /challenges/{id}/check-ins/today 로딩 상태.
 * 지금은 스텁 상세 화면이 쓰지만, 실제 챌린지 상세 화면이 붙으면 그쪽에서 재사용한다.
 */
export function useTodayCheckInStatus(challengeId: number, enabled = true) {
  const active = enabled && !Number.isNaN(challengeId);
  const [status, setStatus] = useState<TodayCheckInStatus | null>(null);
  const [loading, setLoading] = useState(active);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  const reload = useCallback(() => {
    setLoading(true);
    setError(false);
    setReloadKey((k) => k + 1);
  }, []);

  useEffect(() => {
    if (!active) return;
    let cancelled = false;
    getTodayCheckInStatus(challengeId)
      .then((next) => {
        if (cancelled) return;
        setStatus(next);
        setLoading(false);
      })
      .catch(() => {
        if (cancelled) return;
        setError(true);
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [active, challengeId, reloadKey]);

  return { status, loading, error, reload };
}

import { useEffect, useState } from "react";
import { groupErrorMessage } from "../errors";

/** Pass a stable loader. Cleanup prevents old route responses replacing the current page. */
export function useResource<T>(loader: () => Promise<T>) {
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{
    loader: typeof loader;
    attempt: number;
    data?: T;
    error?: string | null;
  }>();
  useEffect(() => {
    let cancelled = false;
    loader()
      .then((data) => {
        if (!cancelled) setResult({ loader, attempt, data });
      })
      .catch((error) => {
        if (!cancelled)
          setResult({ loader, attempt, error: groupErrorMessage(error) });
      });
    return () => {
      cancelled = true;
    };
  }, [loader, attempt]);
  const current =
    result?.loader === loader && result.attempt === attempt
      ? result
      : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: !current,
    retry: () => setAttempt((v) => v + 1),
  };
}

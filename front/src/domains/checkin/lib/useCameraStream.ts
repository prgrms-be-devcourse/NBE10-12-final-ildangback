import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type RefObject,
} from "react";

export type CameraStreamError = "denied" | "error";

interface UseCameraStreamOptions {
  /** false 면 getUserMedia 자체를 시도하지 않는다(예: 녹화 미지원 기기). 기본 true. */
  enabled?: boolean;
}

interface UseCameraStreamResult {
  videoRef: RefObject<HTMLVideoElement | null>;
  /** getUserMedia 실패 사유. 성공/진행 중이면 null. */
  error: CameraStreamError | null;
  /** 트랙을 멈추고 <video> 에서 스트림 참조를 끊는다. */
  stop: () => void;
  /** 실패 상태에서 재시도(스트림을 다시 요청). */
  retry: () => void;
}

/**
 * `CheckInCamera`(사진)와 `CheckInVideoCamera`(영상)가 공유하는 getUserMedia 배선.
 * 뒷면 카메라를 열어 `videoRef` 에 붙이고, 권한 거부/에러를 구분해 노출한다. 준비 완료
 * 판정("ready")과 촬영/녹화 자체는 호출부 책임이다.
 */
export function useCameraStream({
  enabled = true,
}: UseCameraStreamOptions = {}): UseCameraStreamResult {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [error, setError] = useState<CameraStreamError | null>(null);
  const [attempt, setAttempt] = useState(0);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    // 멈춘 스트림 참조를 <video> 에서 끊어 미디어 객체 회수를 앞당긴다.
    if (videoRef.current) videoRef.current.srcObject = null;
  }, []);

  useEffect(() => {
    let cancelled = false;
    // HTTP(비보안 컨텍스트)·일부 인앱 브라우저는 mediaDevices 자체가 없다. getUserMedia
    // 접근 시 동기 TypeError 로 렌더 트리가 죽으므로 호출 자체를 건너뛴다.
    if (!enabled || !navigator.mediaDevices) return;
    // 에러 초기화는 retry() 가 attempt 를 올리기 전에 이미 해둔다 — 여기서 또 하면
    // 렌더 중 동기 setState 가 된다.
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: "environment" }, audio: false })
      .then((stream) => {
        if (cancelled) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        streamRef.current = stream;
        if (videoRef.current) {
          videoRef.current.srcObject = stream;
          // play() 거부(자동재생 정책 등)는 권한 거부가 아니다. muted+playsInline+autoPlay
          // 로 대개 스스로 재생되므로 여기서 실패해도 화면을 막지 않는다.
          void videoRef.current.play().catch(() => {});
        }
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setError(
          err instanceof DOMException && err.name === "NotAllowedError"
            ? "denied"
            : "error",
        );
      });

    return () => {
      cancelled = true;
      stop();
    };
  }, [attempt, enabled, stop]);

  const retry = useCallback(() => {
    if (!navigator.mediaDevices) return; // 되돌릴 수 없는 환경 — 재시도 무의미
    setError(null);
    setAttempt((n) => n + 1);
  }, []);

  return { videoRef, error, stop, retry };
}

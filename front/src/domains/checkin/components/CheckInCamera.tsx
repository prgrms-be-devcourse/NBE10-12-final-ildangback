import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { captureSquareJpeg } from "../lib/squareCapture";

interface Props {
  onCaptured: (blob: Blob) => void;
}

type CamState = "starting" | "ready" | "denied" | "error";

/**
 * 즉석 촬영 전용 카메라. getUserMedia 로 뒷면 카메라를 열고, 뷰파인더는 정사각으로
 * 마스킹한다. 실제 크롭은 촬영 시 canvas 가 한다(ADR 0001). 갤러리에서 불러오는 경로는 없다.
 */
export function CheckInCamera({ onCaptured }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [state, setState] = useState<CamState>("starting");
  const [attempt, setAttempt] = useState(0);
  const [busy, setBusy] = useState(false);

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
  }, []);

  useEffect(() => {
    let cancelled = false;
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
        // "ready" 는 첫 프레임 메타데이터가 온 뒤 <video onLoadedMetadata> 에서 set 한다.
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setState(
          err instanceof DOMException && err.name === "NotAllowedError"
            ? "denied"
            : "error",
        );
      });

    return () => {
      cancelled = true;
      stop();
    };
  }, [attempt, stop]);

  function retry() {
    setState("starting");
    setAttempt((n) => n + 1);
  }

  async function shoot() {
    const video = videoRef.current;
    if (!video || busy) return;
    if (video.videoWidth === 0 || video.videoHeight === 0) return; // 아직 첫 프레임 전

    setBusy(true);
    try {
      const blob = await captureSquareJpeg(video);
      stop();
      onCaptured(blob);
    } catch {
      setState("error");
    } finally {
      setBusy(false);
    }
  }

  if (state === "denied" || state === "error") {
    return (
      <div className="flex flex-1 flex-col items-center justify-center px-8 text-center">
        <p className="text-[15px] font-semibold text-gray-900">
          {state === "denied"
            ? "카메라 권한이 필요해요"
            : "카메라를 열 수 없어요"}
        </p>
        <p className="mt-2 text-[13px] text-gray-500">
          {state === "denied"
            ? "브라우저 설정에서 이 사이트의 카메라 접근을 허용한 뒤 다시 시도해 주세요."
            : "카메라를 쓸 수 있는 기기인지 확인한 뒤 다시 시도해 주세요."}
        </p>
        <Button variant="secondary" className="mt-6" onClick={retry}>
          다시 시도
        </Button>
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col items-center px-6 pt-4 pb-8">
      <div className="relative aspect-square w-full max-w-[360px] overflow-hidden rounded-3xl bg-black">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          onLoadedMetadata={() => setState("ready")}
          className="size-full object-cover"
        />
        {state === "starting" && (
          <span
            role="status"
            aria-label="카메라 준비 중"
            className="absolute top-1/2 left-1/2 size-8 -translate-x-1/2 -translate-y-1/2 animate-spin rounded-full border-3 border-white/40 border-t-white"
          />
        )}
      </div>

      <p className="mt-4 text-[13px] text-gray-500">
        오늘의 활동을 정사각 사진으로 담아요
      </p>

      <Button
        className="mt-6"
        onClick={shoot}
        loading={busy}
        disabled={state !== "ready"}
      >
        사진 촬영
      </Button>
    </div>
  );
}

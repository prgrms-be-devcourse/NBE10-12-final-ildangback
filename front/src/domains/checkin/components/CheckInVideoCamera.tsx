import { useCallback, useEffect, useRef, useState } from "react";
import { Button } from "../../../shared/ui/Button";
import { computeSquareCrop } from "../lib/squareCapture";
import { useCameraStream } from "../lib/useCameraStream";
import {
  CHECKIN_VIDEO_DURATION_MS,
  CHECKIN_VIDEO_MAX_EDGE,
  pickRecorderMimeType,
} from "../lib/squareVideoCapture";
import { CameraPermissionError } from "./CameraPermissionError";

interface Props {
  onCaptured: (blob: Blob) => void;
}

type CamState =
  "starting" | "ready" | "recording" | "denied" | "error" | "unsupported";

const FRAME_INTERVAL_MS = 1000 / 30;

/**
 * 즉석 촬영 전용 영상 카메라. `CheckInCamera`(사진)와 같은 뷰파인더 UX를 쓰지만, 촬영 시
 * canvas 로 크롭한 프레임을 매 tick 그려 `MediaRecorder` 에 넘긴다(§checkin-video-design.md).
 *
 * 뷰파인더 자체는 CSS `object-cover` 로만 정사각처럼 "보이는" 것이고(사진 카메라와 동일),
 * 실제 픽셀 단위 크롭은 화면에 없는 canvas 가 한다 — 사용자에게 보이는 화면과 인코딩되는
 * 영상이 분리되어 있어도 시각적으로는 차이가 없다.
 */
export function CheckInVideoCamera({ onCaptured }: Props) {
  const recorderRef = useRef<MediaRecorder | null>(null);
  const drawTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const stopTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 녹화 도중 언마운트(뒤로가기 등)되면 onstop 이 나중에 불려도 부모에 결과를 넘기지 않는다.
  const mountedRef = useRef(true);
  useEffect(
    () => () => {
      mountedRef.current = false;
    },
    [],
  );

  const canRecord =
    typeof MediaRecorder !== "undefined" &&
    typeof HTMLCanvasElement.prototype.captureStream === "function";

  const {
    videoRef,
    error: streamError,
    stop: stopCamera,
    retry: retryStream,
  } = useCameraStream({ enabled: canRecord });

  // "ready" 는 첫 프레임 메타데이터가 온 뒤 <video onLoadedMetadata> 에서 set 한다.
  const [ready, setReady] = useState(false);
  const [recording, setRecording] = useState(false);
  const [captureError, setCaptureError] = useState(false);
  // canRecord(정적 기능 탐지)와 별개로, 지원 코덱이 하나도 없으면 record() 안에서
  // pickRecorderMimeType 이 런타임에 던진다 — 그때도 같은 "unsupported" 화면을 쓴다.
  const [runtimeUnsupported, setRuntimeUnsupported] = useState(false);
  const [elapsedMs, setElapsedMs] = useState(0);

  const state: CamState =
    !canRecord || runtimeUnsupported
      ? "unsupported"
      : recording
        ? "recording"
        : (streamError ??
          (captureError ? "error" : ready ? "ready" : "starting"));

  const clearTimers = useCallback(() => {
    if (drawTimerRef.current != null) {
      clearInterval(drawTimerRef.current);
      drawTimerRef.current = null;
    }
    if (stopTimerRef.current != null) {
      clearTimeout(stopTimerRef.current);
      stopTimerRef.current = null;
    }
  }, []);

  // 녹화용 타이머·recorder 는 카메라 스트림과 수명이 다르므로(재시도 시 스트림은 새로
  // 열리지만 녹화 중이었을 리는 없다) 언마운트에서만 따로 정리한다.
  useEffect(
    () => () => {
      clearTimers();
      recorderRef.current?.stop();
      recorderRef.current = null;
    },
    [clearTimers],
  );

  function retry() {
    if (!canRecord) return; // 되돌릴 수 없는 환경 — 재시도 무의미
    setReady(false);
    setCaptureError(false);
    setRuntimeUnsupported(false);
    retryStream();
  }

  function record() {
    const video = videoRef.current;
    if (!video || state !== "ready") return;
    if (
      video.readyState < 2 ||
      video.videoWidth === 0 ||
      video.videoHeight === 0
    )
      return;

    let mimeType: string;
    try {
      mimeType = pickRecorderMimeType();
    } catch {
      setRuntimeUnsupported(true);
      return;
    }

    const { sx, sy, size, outSize } = computeSquareCrop({
      width: video.videoWidth,
      height: video.videoHeight,
      maxEdge: CHECKIN_VIDEO_MAX_EDGE,
    });

    const canvas = document.createElement("canvas");
    canvas.width = outSize;
    canvas.height = outSize;
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      setCaptureError(true);
      return;
    }

    // 매 tick 현재 비디오 프레임을 정사각으로 크롭해 canvas 에 그린다 — 사진 캡처와
    // 같은 크롭 계산(computeSquareCrop)을 프레임 단위로 반복 적용하는 것뿐이다.
    drawTimerRef.current = setInterval(() => {
      ctx.drawImage(video, sx, sy, size, size, 0, 0, outSize, outSize);
    }, FRAME_INTERVAL_MS);

    const canvasStream = canvas.captureStream();
    const chunks: BlobPart[] = [];
    const recorder = new MediaRecorder(canvasStream, { mimeType });
    recorderRef.current = recorder;

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };
    recorder.onstop = () => {
      clearTimers();
      canvasStream.getTracks().forEach((track) => track.stop());
      canvas.width = 0;
      canvas.height = 0;
      recorderRef.current = null;
      stopCamera();
      setRecording(false);
      if (!mountedRef.current) return;
      if (chunks.length === 0) {
        setCaptureError(true);
        return;
      }
      onCaptured(new Blob(chunks, { type: mimeType }));
    };

    recorder.start();
    setRecording(true);
    setElapsedMs(0);
    const startedAt = Date.now();
    const tickTimer = setInterval(
      () => setElapsedMs(Date.now() - startedAt),
      100,
    );
    stopTimerRef.current = setTimeout(() => {
      clearInterval(tickTimer);
      recorder.stop();
    }, CHECKIN_VIDEO_DURATION_MS);
  }

  if (state === "denied" || state === "error" || state === "unsupported") {
    return (
      <CameraPermissionError
        title={
          state === "denied"
            ? "카메라 권한이 필요해요"
            : state === "unsupported"
              ? "이 기기에서는 영상 녹화를 지원하지 않아요"
              : "카메라를 열 수 없어요"
        }
        description={
          state === "denied"
            ? "브라우저 설정에서 이 사이트의 카메라 접근을 허용한 뒤 다시 시도해 주세요."
            : state === "unsupported"
              ? "대신 사진으로 인증해 주세요."
              : "카메라를 쓸 수 있는 기기인지 확인한 뒤 다시 시도해 주세요."
        }
        onRetry={state === "unsupported" ? undefined : retry}
      />
    );
  }

  const remainingSec = Math.max(
    0,
    (CHECKIN_VIDEO_DURATION_MS - elapsedMs) / 1000,
  ).toFixed(1);

  return (
    <div className="flex flex-1 flex-col items-center px-6 pt-4 pb-8">
      <div className="relative aspect-square w-full max-w-[360px] overflow-hidden rounded-3xl bg-black">
        <video
          ref={videoRef}
          autoPlay
          playsInline
          muted
          onLoadedMetadata={() => setReady(true)}
          className="size-full object-cover"
        />
        {state === "starting" && (
          <span
            role="status"
            aria-label="카메라 준비 중"
            className="absolute top-1/2 left-1/2 size-8 -translate-x-1/2 -translate-y-1/2 animate-spin rounded-full border-3 border-white/40 border-t-white"
          />
        )}
        {state === "recording" && (
          <span className="absolute top-3 left-3 flex items-center gap-1.5 rounded-full bg-black/50 px-2.5 py-1 text-[12px] font-semibold text-white">
            <span className="size-2 animate-pulse rounded-full bg-red-500" />
            {remainingSec}초
          </span>
        )}
      </div>

      <p className="mt-4 text-[13px] text-gray-500">
        {state === "recording"
          ? "녹화 중이에요. 2초 뒤 자동으로 멈춰요"
          : "오늘의 활동을 정사각 영상으로 담아요"}
      </p>

      <Button
        className="mt-6"
        onClick={record}
        loading={state === "recording"}
        disabled={state !== "ready"}
      >
        영상 촬영
      </Button>
    </div>
  );
}

import { useEffect, useReducer, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { ApiError, SessionExpiredError } from "../../../shared/api/client";
import { isWithinCheckInFileLimit } from "../../../shared/lib/checkinValidation";
import { useToast } from "../../../shared/lib/useToast";
import { TopBar } from "../../../shared/ui/TopBar";
import { submitCheckIn } from "../api";
import { CheckInCamera } from "../components/CheckInCamera";
import { CheckInConfirm } from "../components/CheckInConfirm";
import { CheckInDone } from "../components/CheckInDone";
import { CheckInIntro } from "../components/CheckInIntro";
import { CheckInVideoCamera } from "../components/CheckInVideoCamera";
import { checkInFlowReducer, initialCheckInFlow } from "../lib/checkInFlow";
import { parseCheckInTypeParam } from "../lib/checkInTypeParam";

/**
 * 사진/영상 인증 제출 플로우 (와이어프레임 2·3·4 + 카메라).
 * 미디어 blob 이 메모리에만 있어서 라우트를 쪼개지 않고 스텝 상태로 돌린다.
 *
 * 인증 방법(사진/영상)은 `?type=video` 쿼리로 들어온다(ChallengeDashboard 가 붙여서 이동).
 * 없으면 사진으로 취급 — 기존 딥링크·북마크와 호환.
 */
export function CheckInPage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { challengeId: challengeIdParam } = useParams();
  const [searchParams] = useSearchParams();
  const challengeId = Number(challengeIdParam);
  const detailPath = `/challenges/${challengeIdParam}`;
  const checkInType = parseCheckInTypeParam(searchParams);

  const [flow, dispatch] = useReducer(
    checkInFlowReducer,
    checkInType,
    initialCheckInFlow,
  );
  const [submitting, setSubmitting] = useState(false);
  const previewUrlRef = useRef<string | null>(null);

  function releasePreview() {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = null;
    }
  }

  useEffect(() => releasePreview, []);

  function handleCaptured(blob: Blob) {
    releasePreview();
    const previewUrl = URL.createObjectURL(blob);
    previewUrlRef.current = previewUrl;
    dispatch({ type: "captured", photo: { blob, previewUrl } });
  }

  function goBack() {
    if (submitting) return; // 제출 중에는 뒤로가기를 막는다
    if (flow.step === "intro") {
      navigate(detailPath);
      return;
    }
    if (flow.step === "confirm") releasePreview();
    dispatch({ type: "back" });
  }

  function handleRetake() {
    releasePreview();
    dispatch({ type: "retake" });
  }

  async function handleSubmit(memo: string) {
    if (!flow.photo || submitting) return;

    const kind = flow.checkInType === "VIDEO" ? "video" : "photo";
    if (!isWithinCheckInFileLimit(flow.photo.blob.size, kind)) {
      showToast(
        flow.checkInType === "VIDEO"
          ? "영상 용량이 너무 커요. 다시 촬영해 주세요."
          : "사진 용량이 너무 커요. 다시 촬영해 주세요.",
      );
      return;
    }

    setSubmitting(true);
    try {
      const result = await submitCheckIn(challengeId, {
        checkInType: flow.checkInType,
        media: flow.photo.blob,
        memo: memo || undefined,
      });
      dispatch({ type: "submitted", result });
    } catch (err) {
      if (err instanceof SessionExpiredError) return; // AuthProvider 가 처리
      showToast(
        err instanceof ApiError
          ? err.message
          : "네트워크에 연결할 수 없어요. 잠시 후 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-1 flex-col">
      {flow.step !== "done" && (
        <TopBar className="bg-transparent" onBack={goBack} />
      )}

      {flow.step === "intro" && (
        <CheckInIntro
          checkInType={flow.checkInType}
          onStart={() => dispatch({ type: "startCamera" })}
        />
      )}

      {flow.step === "camera" &&
        (flow.checkInType === "VIDEO" ? (
          <CheckInVideoCamera onCaptured={handleCaptured} />
        ) : (
          <CheckInCamera onCaptured={handleCaptured} />
        ))}

      {flow.step === "confirm" && flow.photo && (
        <CheckInConfirm
          checkInType={flow.checkInType}
          photo={flow.photo}
          submitting={submitting}
          onRetake={handleRetake}
          onSubmit={handleSubmit}
        />
      )}

      {flow.step === "done" && flow.result && (
        <CheckInDone
          result={flow.result}
          onGoToGroup={() => navigate(detailPath)}
        />
      )}
    </div>
  );
}

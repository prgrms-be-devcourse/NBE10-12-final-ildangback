import type {
  CapturedPhoto,
  CheckInResultResponse,
  CheckInType,
} from "../types";

/**
 * 사진/영상 인증 제출 플로우의 스텝 머신.
 *
 * intro → camera → confirm → done. 미디어 blob 이 메모리에만 있어서 라우트를 쪼개지 않고
 * 한 라우트 안에서 스텝만 옮긴다. done 은 종료 상태다.
 *
 * `checkInType` 은 방법 선택 시트에서 정해져 라우트 진입 시 고정되고, 스텝이 바뀌는 동안
 * 절대 바뀌지 않는다(카메라 → 컨펌 화면에서 사진/영상 UI를 가르는 기준).
 *
 * 뒤로가기(Q1, 그릴링): camera/confirm 에서는 이전 스텝으로, intro 에서는 상태를 그대로
 * 두고 호출부가 라우트를 되돌린다.
 */
export type CheckInStep = "intro" | "camera" | "confirm" | "done";

export interface CheckInFlowState {
  checkInType: CheckInType;
  step: CheckInStep;
  photo: CapturedPhoto | null;
  result: CheckInResultResponse | null;
}

export type CheckInFlowAction =
  | { type: "startCamera" }
  | { type: "captured"; photo: CapturedPhoto }
  | { type: "retake" }
  | { type: "submitted"; result: CheckInResultResponse }
  | { type: "back" };

export function initialCheckInFlow(
  checkInType: CheckInType = "PHOTO",
): CheckInFlowState {
  return { checkInType, step: "intro", photo: null, result: null };
}

export function checkInFlowReducer(
  state: CheckInFlowState,
  action: CheckInFlowAction,
): CheckInFlowState {
  if (state.step === "done") return state;

  switch (action.type) {
    case "startCamera":
      return state.step === "intro" ? { ...state, step: "camera" } : state;

    case "captured":
      return state.step === "camera"
        ? { ...state, step: "confirm", photo: action.photo, result: null }
        : state;

    case "retake":
      return state.step === "confirm"
        ? { ...state, step: "camera", photo: null, result: null }
        : state;

    case "submitted":
      return state.step === "confirm"
        ? { ...state, step: "done", result: action.result }
        : state;

    case "back":
      if (state.step === "confirm")
        return { ...state, step: "camera", photo: null, result: null };
      if (state.step === "camera") return { ...state, step: "intro" };
      return state;
  }
}

import type { CapturedPhoto, CheckInResultResponse } from "../types";

/**
 * 사진 인증 제출 플로우의 스텝 머신.
 *
 * intro → camera → confirm → done. 사진 blob 이 메모리에만 있어서 라우트를 쪼개지 않고
 * 한 라우트 안에서 스텝만 옮긴다. done 은 종료 상태다.
 *
 * 뒤로가기(Q1, 그릴링): camera/confirm 에서는 이전 스텝으로, intro 에서는 상태를 그대로
 * 두고 호출부가 라우트를 되돌린다.
 */
export type CheckInStep = "intro" | "camera" | "confirm" | "done";

export interface CheckInFlowState {
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

export const initialCheckInFlow: CheckInFlowState = {
  step: "intro",
  photo: null,
  result: null,
};

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
        ? { step: "confirm", photo: action.photo, result: null }
        : state;

    case "retake":
      return state.step === "confirm"
        ? { step: "camera", photo: null, result: null }
        : state;

    case "submitted":
      return state.step === "confirm"
        ? { step: "done", photo: state.photo, result: action.result }
        : state;

    case "back":
      if (state.step === "confirm")
        return { step: "camera", photo: null, result: null };
      if (state.step === "camera") return { ...state, step: "intro" };
      return state;
  }
}

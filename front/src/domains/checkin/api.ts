import { apiFetch } from "../../shared/api/client";
import {
  isCheckInStubEnabled,
  stubSubmitResult,
  stubTodayStatus,
} from "./devStub";
import type {
  CheckInResultResponse,
  CheckInType,
  TodayCheckInStatus,
} from "./types";

/** GET /challenges/{challengeId}/check-ins/today */
export function getTodayCheckInStatus(
  challengeId: number,
): Promise<TodayCheckInStatus> {
  if (isCheckInStubEnabled()) return Promise.resolve(stubTodayStatus());
  return apiFetch(`/api/challenges/${challengeId}/check-ins/today`);
}

export interface SubmitCheckInInput {
  checkInType: CheckInType;
  /** 카메라에서 만든 정사각 jpeg */
  media: Blob;
  memo?: string;
}

/** POST /challenges/{challengeId}/check-ins (multipart/form-data) */
export function submitCheckIn(
  challengeId: number,
  input: SubmitCheckInInput,
): Promise<CheckInResultResponse> {
  if (isCheckInStubEnabled()) {
    return new Promise((resolve) =>
      setTimeout(() => resolve(stubSubmitResult({ memo: input.memo })), 400),
    );
  }

  const form = new FormData();
  form.append("checkInType", input.checkInType);
  form.append("media", input.media, "check-in.jpg");
  if (input.memo) form.append("memo", input.memo);

  return apiFetch(`/api/challenges/${challengeId}/check-ins`, {
    method: "POST",
    body: form,
  });
}

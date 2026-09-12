import { describe, expect, it } from "vitest";
import type { CapturedPhoto, CheckInResultResponse } from "../types";
import { checkInFlowReducer, initialCheckInFlow } from "./checkInFlow";

const photo: CapturedPhoto = {
  blob: new Blob(["x"], { type: "image/jpeg" }),
  previewUrl: "blob:preview",
};

const result: CheckInResultResponse = {
  checkIn: {
    id: 1,
    userId: 9,
    nickname: "나",
    businessDate: "2026-09-02",
    roundNo: 1,
    checkInType: "PHOTO",
    mediaUrl: "https://cdn/1.jpg",
    mediaType: "IMAGE",
    memo: null,
    createdAt: "2026-09-02T10:00:00",
  },
  currentCount: 1,
  targetCount: 1,
  dailyCompleted: true,
  earnedUserPoints: 10,
  currentStreak: 12,
  groupCompletedCount: 4,
  groupTotalCount: 5,
};

describe("checkInFlowReducer", () => {
  it("starts at the intro step with nothing captured", () => {
    expect(initialCheckInFlow).toEqual({
      step: "intro",
      photo: null,
      result: null,
    });
  });

  it("moves intro -> camera on startCamera", () => {
    const next = checkInFlowReducer(initialCheckInFlow, {
      type: "startCamera",
    });
    expect(next.step).toBe("camera");
  });

  it("moves camera -> confirm and keeps the photo on captured", () => {
    const camera = checkInFlowReducer(initialCheckInFlow, {
      type: "startCamera",
    });
    const next = checkInFlowReducer(camera, { type: "captured", photo });
    expect(next).toEqual({ step: "confirm", photo, result: null });
  });

  it("moves confirm -> camera and drops the photo on retake", () => {
    const confirm = { step: "confirm" as const, photo, result: null };
    const next = checkInFlowReducer(confirm, { type: "retake" });
    expect(next).toEqual({ step: "camera", photo: null, result: null });
  });

  it("moves confirm -> camera and drops the photo on back", () => {
    const confirm = { step: "confirm" as const, photo, result: null };
    const next = checkInFlowReducer(confirm, { type: "back" });
    expect(next).toEqual({ step: "camera", photo: null, result: null });
  });

  it("moves camera -> intro on back", () => {
    const camera = { step: "camera" as const, photo: null, result: null };
    expect(checkInFlowReducer(camera, { type: "back" }).step).toBe("intro");
  });

  it("leaves intro unchanged on back (caller navigates the route)", () => {
    expect(checkInFlowReducer(initialCheckInFlow, { type: "back" })).toBe(
      initialCheckInFlow,
    );
  });

  it("moves confirm -> done and stores the result on submitted", () => {
    const confirm = { step: "confirm" as const, photo, result: null };
    const next = checkInFlowReducer(confirm, { type: "submitted", result });
    expect(next).toEqual({ step: "done", photo, result });
  });

  it("ignores captured/retake/submitted from an unexpected step", () => {
    expect(
      checkInFlowReducer(initialCheckInFlow, { type: "captured", photo }),
    ).toBe(initialCheckInFlow);
    const camera = { step: "camera" as const, photo: null, result: null };
    expect(checkInFlowReducer(camera, { type: "retake" })).toBe(camera);
    expect(checkInFlowReducer(camera, { type: "submitted", result })).toBe(
      camera,
    );
  });

  it("treats done as terminal", () => {
    const done = { step: "done" as const, photo, result };
    expect(checkInFlowReducer(done, { type: "back" })).toBe(done);
    expect(checkInFlowReducer(done, { type: "startCamera" })).toBe(done);
  });
});

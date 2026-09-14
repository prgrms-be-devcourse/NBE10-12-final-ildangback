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
    posterUrl: null,
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
  it("starts at the intro step with nothing captured, defaulting to photo", () => {
    expect(initialCheckInFlow()).toEqual({
      checkInType: "PHOTO",
      step: "intro",
      photo: null,
      result: null,
    });
  });

  it("locks in the check-in type passed at flow start", () => {
    expect(initialCheckInFlow("VIDEO").checkInType).toBe("VIDEO");
  });

  it("moves intro -> camera on startCamera", () => {
    const next = checkInFlowReducer(initialCheckInFlow(), {
      type: "startCamera",
    });
    expect(next.step).toBe("camera");
  });

  it("moves camera -> confirm and keeps the photo on captured", () => {
    const camera = checkInFlowReducer(initialCheckInFlow(), {
      type: "startCamera",
    });
    const next = checkInFlowReducer(camera, { type: "captured", photo });
    expect(next).toEqual({
      checkInType: "PHOTO",
      step: "confirm",
      photo,
      result: null,
    });
  });

  it("moves confirm -> camera and drops the photo on retake", () => {
    const confirm = {
      checkInType: "PHOTO" as const,
      step: "confirm" as const,
      photo,
      result: null,
    };
    const next = checkInFlowReducer(confirm, { type: "retake" });
    expect(next).toEqual({
      checkInType: "PHOTO",
      step: "camera",
      photo: null,
      result: null,
    });
  });

  it("moves confirm -> camera and drops the photo on back", () => {
    const confirm = {
      checkInType: "PHOTO" as const,
      step: "confirm" as const,
      photo,
      result: null,
    };
    const next = checkInFlowReducer(confirm, { type: "back" });
    expect(next).toEqual({
      checkInType: "PHOTO",
      step: "camera",
      photo: null,
      result: null,
    });
  });

  it("moves camera -> intro on back", () => {
    const camera = {
      checkInType: "PHOTO" as const,
      step: "camera" as const,
      photo: null,
      result: null,
    };
    expect(checkInFlowReducer(camera, { type: "back" }).step).toBe("intro");
  });

  it("leaves intro unchanged on back (caller navigates the route)", () => {
    const initial = initialCheckInFlow();
    expect(checkInFlowReducer(initial, { type: "back" })).toBe(initial);
  });

  it("moves confirm -> done and stores the result on submitted", () => {
    const confirm = {
      checkInType: "PHOTO" as const,
      step: "confirm" as const,
      photo,
      result: null,
    };
    const next = checkInFlowReducer(confirm, { type: "submitted", result });
    expect(next).toEqual({
      checkInType: "PHOTO",
      step: "done",
      photo,
      result,
    });
  });

  it("ignores captured/retake/submitted from an unexpected step", () => {
    const initial = initialCheckInFlow();
    expect(checkInFlowReducer(initial, { type: "captured", photo })).toBe(
      initial,
    );
    const camera = {
      checkInType: "PHOTO" as const,
      step: "camera" as const,
      photo: null,
      result: null,
    };
    expect(checkInFlowReducer(camera, { type: "retake" })).toBe(camera);
    expect(checkInFlowReducer(camera, { type: "submitted", result })).toBe(
      camera,
    );
  });

  it("treats done as terminal", () => {
    const done = {
      checkInType: "PHOTO" as const,
      step: "done" as const,
      photo,
      result,
    };
    expect(checkInFlowReducer(done, { type: "back" })).toBe(done);
    expect(checkInFlowReducer(done, { type: "startCamera" })).toBe(done);
  });
});

import { describe, expect, it } from "vitest";
import {
  MAX_CHECKIN_PHOTO_FILE_BYTES,
  MAX_CHECKIN_VIDEO_FILE_BYTES,
  isWithinCheckInFileLimit,
} from "./checkinValidation";

describe("checkinValidation", () => {
  it("caps photo check-in media at 40MB", () => {
    expect(MAX_CHECKIN_PHOTO_FILE_BYTES).toBe(41_943_040);
  });

  it("caps video check-in media at 10MB", () => {
    expect(MAX_CHECKIN_VIDEO_FILE_BYTES).toBe(10_485_760);
  });

  it("accepts a photo at exactly the limit", () => {
    expect(isWithinCheckInFileLimit(41_943_040)).toBe(true);
  });

  it("rejects a photo over the limit", () => {
    expect(isWithinCheckInFileLimit(41_943_041)).toBe(false);
  });

  it("accepts a small photo", () => {
    expect(isWithinCheckInFileLimit(2_000_000)).toBe(true);
  });

  it("accepts a video at exactly its (lower) limit", () => {
    expect(isWithinCheckInFileLimit(10_485_760, "video")).toBe(true);
  });

  it("rejects a video over its limit even though it's under the photo limit", () => {
    expect(isWithinCheckInFileLimit(10_485_761, "video")).toBe(false);
  });
});

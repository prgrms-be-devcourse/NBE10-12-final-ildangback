import { describe, expect, it } from "vitest";
import {
  MAX_CHECKIN_FILE_BYTES,
  isWithinCheckInFileLimit,
} from "./checkinValidation";

describe("checkinValidation", () => {
  it("caps check-in media at 40MB", () => {
    expect(MAX_CHECKIN_FILE_BYTES).toBe(41_943_040);
  });

  it("accepts a file at exactly the limit", () => {
    expect(isWithinCheckInFileLimit(41_943_040)).toBe(true);
  });

  it("rejects a file over the limit", () => {
    expect(isWithinCheckInFileLimit(41_943_041)).toBe(false);
  });

  it("accepts a small file", () => {
    expect(isWithinCheckInFileLimit(2_000_000)).toBe(true);
  });
});

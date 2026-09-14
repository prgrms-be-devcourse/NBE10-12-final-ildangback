import { describe, expect, it } from "vitest";
import { groupCreateSchema } from "./validation";

/** 스키마가 요구하는 다른 필드는 전부 채운 최소 유효 폼. allowedTypes 만 바꿔가며 검증한다. */
function baseForm(allowedTypes: unknown) {
  return {
    name: "오운완",
    description: "",
    category: "EXERCISE",
    mapType: "GYM",
    visibility: "PUBLIC",
    maxMembers: 6,
    challenge: {
      startDate: "2026-09-14",
      endDate: "2026-10-14",
      frequencyType: "DAILY",
      frequencyValue: null,
      daysOfWeek: [],
      dailyCheckInCount: 1,
      allowedTypes,
    },
  };
}

describe("groupCreateSchema.challenge.allowedTypes", () => {
  it("accepts PHOTO", () => {
    expect(groupCreateSchema.safeParse(baseForm(["PHOTO"])).success).toBe(true);
  });

  it("accepts VIDEO", () => {
    expect(groupCreateSchema.safeParse(baseForm(["VIDEO"])).success).toBe(true);
  });

  it("rejects an empty selection", () => {
    expect(groupCreateSchema.safeParse(baseForm([])).success).toBe(false);
  });

  it("accepts picking both at once — 같은 회차라도 사진/영상 둘 다 허용할 수 있다", () => {
    expect(
      groupCreateSchema.safeParse(baseForm(["PHOTO", "VIDEO"])).success,
    ).toBe(true);
  });

  it("rejects LIVE — no subsystem for it yet", () => {
    expect(groupCreateSchema.safeParse(baseForm(["LIVE"])).success).toBe(false);
  });
});

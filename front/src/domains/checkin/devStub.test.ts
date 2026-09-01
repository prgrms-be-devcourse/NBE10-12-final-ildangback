import { describe, expect, it } from "vitest";
import { stubSubmitResult, stubTodayStatus } from "./devStub";

describe("check-in dev stub", () => {
  it("reports an open photo check-in for today", () => {
    const status = stubTodayStatus();
    expect(status).toMatchObject({
      isCheckInDay: true,
      currentCount: 0,
      targetCount: 1,
      completed: false,
      allowedTypes: ["PHOTO"],
    });
    expect(status.businessDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("echoes the memo into the result, or null when omitted", () => {
    expect(stubSubmitResult({ memo: "오늘 완료" }).checkIn.memo).toBe(
      "오늘 완료",
    );
    expect(stubSubmitResult({}).checkIn.memo).toBeNull();
  });

  it("returns a completed daily result with points", () => {
    const result = stubSubmitResult({});
    expect(result.dailyCompleted).toBe(true);
    expect(result.currentCount).toBe(result.targetCount);
    expect(result.earnedUserPoints).toBeGreaterThan(0);
    expect(result.groupCompletedCount).toBeLessThanOrEqual(
      result.groupTotalCount,
    );
  });
});

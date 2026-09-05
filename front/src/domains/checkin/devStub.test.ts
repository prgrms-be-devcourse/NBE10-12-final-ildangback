import { describe, expect, it } from "vitest";
import {
  stubChallengeMembers,
  stubGallery,
  stubSubmitResult,
  stubTodayStatus,
} from "./devStub";

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

describe("gallery dev stub", () => {
  const SEPTEMBER = "2026-09";
  const AUGUST = "2026-08";

  it("pages through a month with a cursor until it runs out", () => {
    const all = stubGallery({ month: SEPTEMBER, size: 1000 }).content;
    expect(all.length).toBeGreaterThan(20); // 무한스크롤이 실제로 돌 만큼

    const collected: number[] = [];
    let cursor: number | undefined;
    let guard = 0;
    for (;;) {
      const res = stubGallery({ month: SEPTEMBER, size: 20, cursor });
      collected.push(...res.content.map((c) => c.id));
      if (!res.meta.hasNext) break;
      cursor = res.meta.nextCursor!;
      expect(cursor).toBe(res.content.at(-1)!.id);
      if (++guard > 10) throw new Error("cursor never terminated");
    }

    expect(collected).toEqual(all.map((c) => c.id)); // 순서·개수 그대로
    expect(new Set(collected).size).toBe(collected.length); // 중복 없음
    expect(collected).toEqual([...collected].sort((a, b) => b - a)); // id 내림차순
  });

  it("filters by month and by participant", () => {
    const august = stubGallery({ month: AUGUST, size: 100 });
    expect(august.content.every((c) => c.businessDate.startsWith(AUGUST))).toBe(
      true,
    );

    const [member] = stubChallengeMembers();
    const mine = stubGallery({ userId: member.userId, size: 100 });
    expect(mine.content.every((c) => c.userId === member.userId)).toBe(true);
    expect(mine.content.length).toBeGreaterThan(0);
  });
});

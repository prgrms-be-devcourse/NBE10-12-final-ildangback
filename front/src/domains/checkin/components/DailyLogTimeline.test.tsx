import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DailyLog, DailyLogCursorResponse } from "../types";
import { DailyLogTimeline } from "./DailyLogTimeline";

vi.mock("../api", () => ({ getDailyLogs: vi.fn() }));
const { getDailyLogs } = await import("../api");

function log(over: Partial<DailyLog> = {}): DailyLog {
  return {
    id: 1,
    businessDate: "2026-09-02",
    videoUrl: null,
    completedCount: 4,
    totalCount: 6,
    ...over,
  };
}

function page(content: DailyLog[]): DailyLogCursorResponse {
  return {
    content,
    meta: {
      nextCursor: null,
      hasNext: false,
      size: 20,
      recordDays: content.length,
      avgRate: 82,
    },
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});

describe("DailyLogTimeline", () => {
  it("shows the month aggregate banner and a dated tile per log", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([
        log({ id: 2, businessDate: "2026-09-02", completedCount: 6 }),
        log({ id: 1, businessDate: "2026-09-01", completedCount: 3 }),
      ]),
    );

    render(<DailyLogTimeline challengeId={1} />);

    expect(await screen.findByText("2일")).toBeInTheDocument(); // recordDays
    expect(screen.getByText("82%")).toBeInTheDocument();
    expect(screen.getByText(/9월 2일 \(/)).toBeInTheDocument();
    expect(screen.getByText(/9월 1일 \(/)).toBeInTheDocument();
  });

  it("renders placeholder tiles filled up to completedCount when there is no video", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([log({ id: 1, completedCount: 4, totalCount: 6 })]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);
    await screen.findByText(/9월 2일 \(/);

    // completedCount(4) 칸은 체크 표시, 나머지 2칸은 검정, 영상 없음
    expect(container.querySelectorAll(".bg-purple-100")).toHaveLength(4);
    expect(container.querySelectorAll(".bg-black")).toHaveLength(2);
    expect(container.querySelector("video")).toBeNull();
  });

  it("lays the tile grid out by participant count", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([
        log({
          id: 3,
          businessDate: "2026-09-03",
          totalCount: 3,
          completedCount: 3,
        }),
        log({
          id: 2,
          businessDate: "2026-09-02",
          totalCount: 1,
          completedCount: 1,
        }),
        log({
          id: 1,
          businessDate: "2026-09-01",
          totalCount: 5,
          completedCount: 5,
        }),
      ]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);
    await screen.findByText(/9월 1일 \(/);

    const tiles = container.querySelectorAll("li > div > div.grid");
    // 3명 → 한 줄 3칸, 1명 → 1칸, 5명 → 3×2 (6칸, 마지막 검정)
    expect(tiles[0]).toHaveClass("grid-cols-3");
    expect(tiles[0].children).toHaveLength(3);
    expect(tiles[1]).toHaveClass("grid-cols-1");
    expect(tiles[1].children).toHaveLength(1);
    expect(tiles[2]).toHaveClass("grid-cols-3");
    expect(tiles[2].children).toHaveLength(6);
  });

  it("shows a text-only notice instead of tiles when nobody checked in that day", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([log({ id: 1, completedCount: 0, totalCount: 5 })]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);
    expect(
      await screen.findByText("이날은 아무도 인증하지 않았어요"),
    ).toBeInTheDocument();
    // 타일/영상 자리 자체가 없다
    expect(container.querySelectorAll("img")).toHaveLength(0);
    expect(container.querySelector("video")).toBeNull();
    expect(container.querySelector("div.grid")).toBeNull();
  });

  it("shows the empty state", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(page([]));
    render(<DailyLogTimeline challengeId={1} />);
    expect(
      await screen.findByText("이번 달 일일 로그가 없어요"),
    ).toBeInTheDocument();
  });
});

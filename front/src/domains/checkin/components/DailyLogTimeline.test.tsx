import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DailyLog, DailyLogCursorResponse } from "../types";
import { DailyLogTimeline } from "./DailyLogTimeline";

vi.mock("../api", () => ({ getDailyLogs: vi.fn() }));
// 영상 타일이 useAuthedImage 로 mp4 를 받는다. 그 경로만 막으면 나머지는 실제 코드가 돈다.
vi.mock("../../../shared/api/client", () => ({ apiFetchBlob: vi.fn() }));
const { getDailyLogs } = await import("../api");
const { apiFetchBlob } = await import("../../../shared/api/client");

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
  // jsdom 에는 objectURL 이 없다.
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:daily-log-1"),
    revokeObjectURL: vi.fn(),
  });
  // 관찰을 걸면 바로 "보인다" 고 답한다 — 영상 타일이 뷰포트 진입까지 fetch 를 미루기 때문.
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      // 파라미터 프로퍼티는 erasableSyntaxOnly 가 막는다. 필드로 따로 둔다.
      cb: IntersectionObserverCallback;
      constructor(cb: IntersectionObserverCallback) {
        this.cb = cb;
      }
      observe() {
        this.cb(
          [{ isIntersecting: true } as IntersectionObserverEntry],
          this as unknown as IntersectionObserver,
        );
      }
      unobserve() {}
      disconnect() {}
    },
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
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

  it("shows a text-only notice instead of tiles when nobody met the target", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([log({ id: 1, completedCount: 0, totalCount: 5 })]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);
    expect(
      await screen.findByText("이날은 목표를 채운 사람이 없어요"),
    ).toBeInTheDocument();
    // 타일/영상 자리 자체가 없다
    expect(container.querySelectorAll("img")).toHaveLength(0);
    expect(container.querySelector("video")).toBeNull();
    expect(container.querySelector("div.grid")).toBeNull();
  });

  it("fetches the montage with auth and plays it from an objectURL", async () => {
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(["mp4"]));
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([log({ id: 1, videoUrl: "/api/daily-logs/1/media" })]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);

    // <video src> 로는 Authorization 헤더가 안 실려서, fetch 로 받아 objectURL 로 건다.
    await waitFor(() =>
      expect(container.querySelector("video")).toHaveAttribute(
        "src",
        "blob:daily-log-1",
      ),
    );
    expect(apiFetchBlob).toHaveBeenCalledWith("/api/daily-logs/1/media");
    expect(container.querySelector("div.grid")).toBeNull();

    // 풀스크린은 타일이 이미 받아둔 objectURL 을 그대로 쓴다 — 다시 안 내려받는다.
    screen.getByRole("button", { name: "" }).click();
    const dialog = await screen.findByRole("dialog", {
      name: "일일 로그 영상",
    });
    expect(dialog.querySelector("video")).toHaveAttribute(
      "src",
      "blob:daily-log-1",
    );
    expect(apiFetchBlob).toHaveBeenCalledTimes(1);
  });

  it("holds the placeholder layout until the montage arrives", async () => {
    // 영상이 안 받히는 동안에도 타일 배치는 그대로 — 붙는 순간 배치가 튀지 않게.
    // src 를 테스트마다 다르게 둔다: useAuthedImage 의 inflight 맵이 모듈 전역이라
    // 끝나지 않는 fetch 를 같은 경로로 걸면 다음 테스트가 그 promise 를 물려받는다.
    vi.mocked(apiFetchBlob).mockReturnValue(new Promise(() => {}));
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([
        log({
          id: 2,
          videoUrl: "/api/daily-logs/2/media",
          completedCount: 4,
          totalCount: 6,
        }),
      ]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);
    await screen.findByText(/9월 2일 \(/);

    expect(container.querySelector("video")).toBeNull();
    expect(container.querySelectorAll(".bg-purple-100")).toHaveLength(4);
    expect(container.querySelectorAll(".bg-black")).toHaveLength(2);
  });

  it("shows the montage even on a day nobody met the target", async () => {
    // 마감 배치가 지난 날 로그를 훑어 영상을 만들기 때문에 completedCount 0 인 날도 영상이 붙는다.
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(["mp4"]));
    vi.mocked(getDailyLogs).mockResolvedValue(
      page([
        log({
          id: 3,
          videoUrl: "/api/daily-logs/3/media",
          completedCount: 0,
          totalCount: 5,
        }),
      ]),
    );

    const { container } = render(<DailyLogTimeline challengeId={1} />);

    await waitFor(() =>
      expect(container.querySelector("video")).toBeInTheDocument(),
    );
    expect(
      screen.queryByText("이날은 목표를 채운 사람이 없어요"),
    ).not.toBeInTheDocument();
  });

  it("shows the empty state", async () => {
    vi.mocked(getDailyLogs).mockResolvedValue(page([]));
    render(<DailyLogTimeline challengeId={1} />);
    expect(
      await screen.findByText("이번 달 일일 로그가 없어요"),
    ).toBeInTheDocument();
  });
});

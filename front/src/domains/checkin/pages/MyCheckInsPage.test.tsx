import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { MyCheckIn, MyCheckInCursorResponse } from "../types";
import { MyCheckInsPage } from "./MyCheckInsPage";

vi.mock("../api", () => ({
  getMyCheckIns: vi.fn(),
  getMyChallenges: vi.fn(),
}));

const { getMyCheckIns, getMyChallenges } = await import("../api");

function myCheckIn(over: Partial<MyCheckIn> = {}): MyCheckIn {
  return {
    id: 1,
    userId: 1,
    nickname: "나",
    businessDate: "2026-09-02",
    roundNo: 1,
    checkInType: "PHOTO",
    mediaUrl: "https://cdn/1.jpg",
    mediaType: "IMAGE",
    memo: null,
    createdAt: "2026-09-02T09:00:00",
    challengeId: 1,
    ...over,
  };
}

function pageOf(
  content: MyCheckIn[],
  totalCount: number,
): MyCheckInCursorResponse {
  return {
    content,
    meta: { nextCursor: null, hasNext: false, size: 20, totalCount },
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
  vi.mocked(getMyChallenges).mockResolvedValue([
    { challengeId: 1, name: "오운완" },
    { challengeId: 2, name: "매일 독서 30분" },
  ]);
});

describe("MyCheckInsPage", () => {
  it("shows the total count and a date-grouped grid, no author badge", async () => {
    vi.mocked(getMyCheckIns).mockResolvedValue(
      pageOf(
        [
          myCheckIn({ id: 2, businessDate: "2026-09-02" }),
          myCheckIn({ id: 1, businessDate: "2026-09-01" }),
        ],
        286,
      ),
    );

    render(
      <MemoryRouter>
        <MyCheckInsPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText("286")).toBeInTheDocument();
    expect(screen.getByText("9월 2일")).toBeInTheDocument();
    expect(screen.getByText("9월 1일")).toBeInTheDocument();
    expect(
      screen.queryByText("나", { selector: "span" }),
    ).not.toBeInTheDocument();
  });

  it("refetches with challengeId when the challenge dropdown changes", async () => {
    vi.mocked(getMyCheckIns).mockResolvedValue(pageOf([myCheckIn()], 10));

    render(
      <MemoryRouter>
        <MyCheckInsPage />
      </MemoryRouter>,
    );
    await screen.findByText("10");

    await userEvent.selectOptions(
      screen.getByLabelText("챌린지 선택"),
      "매일 독서 30분",
    );

    await waitFor(() => {
      expect(getMyCheckIns).toHaveBeenLastCalledWith(
        expect.objectContaining({ challengeId: 2 }),
      );
    });
  });
});

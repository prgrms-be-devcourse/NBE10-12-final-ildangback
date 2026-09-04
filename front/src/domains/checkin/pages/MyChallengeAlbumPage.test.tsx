import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ToastProvider } from "../../../shared/ui/ToastProvider";
import type { MyCheckIn, MyCheckInCursorResponse } from "../types";
import { MyChallengeAlbumPage } from "./MyChallengeAlbumPage";

vi.mock("../api", () => ({
  getMyCheckIns: vi.fn(),
  getChallengeAlbumSummary: vi.fn(),
}));

const { getMyCheckIns, getChallengeAlbumSummary } = await import("../api");

function pageOf(
  content: MyCheckIn[],
  totalCount: number,
): MyCheckInCursorResponse {
  return {
    content,
    meta: { nextCursor: null, hasNext: false, size: 20, totalCount },
  };
}

function renderAt(id: number) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[`/profile/challenges/${id}/album`]}>
        <Routes>
          <Route
            path="/profile/challenges/:challengeId/album"
            element={<MyChallengeAlbumPage />}
          />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  );
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
  vi.mocked(getChallengeAlbumSummary).mockResolvedValue({
    challengeId: 2,
    name: "매일 독서 30분",
    category: "독서",
    active: true,
    startDate: "2026-08-20",
    endDate: "2027-02-15",
  });
  vi.mocked(getMyCheckIns).mockResolvedValue(pageOf([], 84));
});

describe("MyChallengeAlbumPage", () => {
  it("shows the challenge header, period and my count, scoped to the challenge", async () => {
    renderAt(2);

    expect(await screen.findByText("매일 독서 30분 앨범")).toBeInTheDocument();
    expect(screen.getByText("독서")).toBeInTheDocument();
    expect(screen.getByText("진행 중")).toBeInTheDocument();
    expect(screen.getByText(/8\.20 - 2027\.02\.15/)).toBeInTheDocument();
    expect(screen.getByText(/내 인증 84개/)).toBeInTheDocument();

    expect(getMyCheckIns).toHaveBeenCalledWith(
      expect.objectContaining({ challengeId: 2 }),
    );
  });

  it("toasts instead of filtering on the unsupported video chip", async () => {
    renderAt(2);
    await screen.findByText("매일 독서 30분 앨범");

    await userEvent.click(screen.getByRole("button", { name: "영상" }));

    expect(
      await screen.findByText("영상 인증은 아직 지원하지 않아요"),
    ).toBeInTheDocument();
  });
});

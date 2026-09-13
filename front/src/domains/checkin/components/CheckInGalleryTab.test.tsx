import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CheckIn, CheckInCursorResponse } from "../types";
import { CheckInGalleryTab } from "./CheckInGalleryTab";

vi.mock("../api", () => ({
  getChallengeGallery: vi.fn(),
  getChallengeMembers: vi.fn(),
}));

const { getChallengeGallery, getChallengeMembers } = await import("../api");

function checkIn(over: Partial<CheckIn> = {}): CheckIn {
  return {
    id: 1,
    userId: 1,
    nickname: "Ari",
    businessDate: "2026-09-02",
    roundNo: 1,
    checkInType: "PHOTO",
    mediaUrl: "https://cdn/1.jpg",
    mediaType: "IMAGE",
    memo: null,
    createdAt: "2026-09-02T09:00:00",
    ...over,
  };
}

function pageOf(content: CheckIn[]): CheckInCursorResponse {
  return { content, meta: { nextCursor: null, hasNext: false, size: 20 } };
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
  vi.mocked(getChallengeMembers).mockResolvedValue([
    { userId: 1, nickname: "Ari", todayCheckInCount: 0 },
    { userId: 2, nickname: "Noah", todayCheckInCount: 1 },
  ]);
});

describe("CheckInGalleryTab", () => {
  it("renders a photo grid with author initials, no memo on the tile", async () => {
    vi.mocked(getChallengeGallery).mockResolvedValue(
      pageOf([
        checkIn({ id: 2, userId: 2, nickname: "Noah", memo: "30분 러닝" }),
        checkIn({ id: 1, userId: 1, nickname: "Ari", memo: null }),
      ]),
    );

    render(<CheckInGalleryTab challengeId={1} />);

    expect(await screen.findAllByRole("img", { name: /인증/ })).toHaveLength(2);
    // 닉네임 뱃지는 뜨지만 memo 는 타일에 안 나온다
    expect(screen.getByText("Noah", { selector: "span" })).toBeInTheDocument();
    expect(screen.queryByText("30분 러닝")).not.toBeInTheDocument();
  });

  it("opens the lightbox with time and memo when a tile is tapped", async () => {
    vi.mocked(getChallengeGallery).mockResolvedValue(
      pageOf([
        checkIn({
          id: 2,
          nickname: "Noah",
          memo: "30분 러닝",
          createdAt: "2026-09-02T14:32:00",
        }),
      ]),
    );

    render(<CheckInGalleryTab challengeId={1} />);
    const tile = await screen.findByRole("button", { name: /Noah의 인증/ });
    await userEvent.click(tile);

    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("30분 러닝")).toBeInTheDocument();
    expect(screen.getByText("9월 2일 14:32")).toBeInTheDocument();
  });

  it("shows the empty state when there are no check-ins", async () => {
    vi.mocked(getChallengeGallery).mockResolvedValue(pageOf([]));
    render(<CheckInGalleryTab challengeId={1} />);
    expect(
      await screen.findByText("이번 달 인증이 없어요"),
    ).toBeInTheDocument();
  });

  it("moves the month and refetches", async () => {
    vi.mocked(getChallengeGallery).mockResolvedValue(pageOf([checkIn()]));
    render(<CheckInGalleryTab challengeId={1} />);
    await screen.findByRole("button", { name: /Ari의 인증/ });

    const shown = screen.getByText(/\d{4}년 \d{1,2}월/).textContent!;
    await userEvent.click(screen.getByRole("button", { name: "이전 달" }));

    await waitFor(() => {
      expect(screen.getByText(/\d{4}년 \d{1,2}월/).textContent).not.toBe(shown);
    });
    expect(getChallengeGallery).toHaveBeenCalledTimes(2);
  });

  it("filters by participant chip", async () => {
    vi.mocked(getChallengeGallery).mockResolvedValue(pageOf([checkIn()]));
    render(<CheckInGalleryTab challengeId={1} />);
    await screen.findByRole("button", { name: /Ari의 인증/ });

    await userEvent.click(screen.getByRole("button", { name: "Noah" }));

    await waitFor(() => {
      expect(getChallengeGallery).toHaveBeenLastCalledWith(
        1,
        expect.objectContaining({ userId: 2 }),
      );
    });
  });
});

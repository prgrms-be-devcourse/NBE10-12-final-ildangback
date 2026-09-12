import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { CheckInResultResponse } from "../types";
import { CheckInDone } from "./CheckInDone";

const base: CheckInResultResponse = {
  checkIn: {
    id: 1,
    userId: 1,
    nickname: "나",
    businessDate: "2026-09-02",
    roundNo: 1,
    checkInType: "PHOTO",
    mediaUrl: "https://cdn/1.jpg",
    mediaType: "IMAGE",
    memo: null,
    createdAt: "2026-09-02T10:00:00",
  },
  currentCount: 1,
  targetCount: 1,
  dailyCompleted: true,
  earnedUserPoints: 10,
  currentStreak: 12,
  groupCompletedCount: 4,
  groupTotalCount: 5,
};

describe("CheckInDone", () => {
  it("shows the earned personal points when the daily goal is met", () => {
    render(<CheckInDone result={base} onGoToGroup={() => {}} />);
    expect(screen.getByText(/개인 포인트/)).toBeInTheDocument();
    expect(screen.getByText(/\+10P/)).toBeInTheDocument();
  });

  it("never shows a group points row (paid at daily-log time, not here)", () => {
    render(<CheckInDone result={base} onGoToGroup={() => {}} />);
    expect(screen.queryByText(/그룹 포인트/)).not.toBeInTheDocument();
  });

  it("shows an encouragement instead of points when the goal is not yet met", () => {
    render(
      <CheckInDone
        result={{
          ...base,
          currentCount: 1,
          targetCount: 3,
          dailyCompleted: false,
          earnedUserPoints: 0,
        }}
        onGoToGroup={() => {}}
      />,
    );
    expect(screen.queryByText(/개인 포인트/)).not.toBeInTheDocument();
    expect(screen.getByText(/2회 더/)).toBeInTheDocument();
  });

  it("always shows the streak and group completion status", () => {
    render(
      <CheckInDone
        result={{ ...base, dailyCompleted: false, earnedUserPoints: 0 }}
        onGoToGroup={() => {}}
      />,
    );
    expect(screen.getByText(/12일 연속/)).toBeInTheDocument();
    expect(screen.getByText(/4\s*\/\s*5\s*명/)).toBeInTheDocument();
  });

  it("navigates to the group view on button press", async () => {
    const onGoToGroup = vi.fn();
    render(<CheckInDone result={base} onGoToGroup={onGoToGroup} />);
    await userEvent.click(
      screen.getByRole("button", { name: "그룹 현황으로 이동" }),
    );
    expect(onGoToGroup).toHaveBeenCalledOnce();
  });
});

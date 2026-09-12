import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { TodayCheckInStatus } from "../types";
import { CheckInMethodSheet } from "./CheckInMethodSheet";

const openStatus: TodayCheckInStatus = {
  businessDate: "2026-09-02",
  isCheckInDay: true,
  currentCount: 0,
  targetCount: 1,
  completed: false,
  allowedTypes: ["PHOTO"],
};

function renderSheet(
  overrides: Partial<Parameters<typeof CheckInMethodSheet>[0]> = {},
) {
  const onSelectPhoto = vi.fn();
  const onClose = vi.fn();
  render(
    <CheckInMethodSheet
      isOpen
      onClose={onClose}
      loading={false}
      status={openStatus}
      onSelectPhoto={onSelectPhoto}
      {...overrides}
    />,
  );
  return { onSelectPhoto, onClose };
}

describe("CheckInMethodSheet", () => {
  it("shows today's progress count", () => {
    renderSheet();
    expect(screen.getByText("0")).toBeInTheDocument();
    expect(screen.getByText(/\/\s*1회/)).toBeInTheDocument();
  });

  it("selects photo check-in when the photo option is tapped", async () => {
    const { onSelectPhoto } = renderSheet();
    await userEvent.click(screen.getByRole("button", { name: /사진 인증/ }));
    expect(onSelectPhoto).toHaveBeenCalledOnce();
  });

  it("disables the photo option when the challenge does not allow photos", () => {
    renderSheet({ status: { ...openStatus, allowedTypes: [] } });
    expect(screen.getByRole("button", { name: /사진 인증/ })).toBeDisabled();
  });

  it("shows the video option as disabled with a coming-soon note", () => {
    renderSheet();
    const video = screen.getByRole("button", { name: /영상 인증/ });
    expect(video).toBeDisabled();
    expect(screen.getByText(/추후|준비 중/)).toBeInTheDocument();
  });

  it("does not offer live check-in", () => {
    renderSheet();
    expect(screen.queryByText(/라이브/)).not.toBeInTheDocument();
  });

  it("shows a loading state without method options", () => {
    renderSheet({ loading: true, status: null });
    expect(screen.getByRole("status")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /사진 인증/ }),
    ).not.toBeInTheDocument();
  });

  it("shows a fallback message when the status could not be loaded", () => {
    renderSheet({ loading: false, error: true, status: null });
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
    expect(screen.getByText(/불러올 수 없어요/)).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /사진 인증/ }),
    ).not.toBeInTheDocument();
  });
});

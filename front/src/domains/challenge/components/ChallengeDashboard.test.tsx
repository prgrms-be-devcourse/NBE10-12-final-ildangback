import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "../../../shared/api/client";
import { nudgeMember } from "../api";
import type { ChallengeStatusResponse } from "../types";
import { ChallengeDashboard } from "./ChallengeDashboard";
const { showToast } = vi.hoisted(() => ({ showToast: vi.fn() }));
vi.mock("../api", () => ({ nudgeMember: vi.fn() }));
vi.mock("../../../shared/lib/useToast", () => ({
  useToast: () => ({ showToast }),
}));
vi.mock("./ExtensionChoicePanel", () => ({ ExtensionChoicePanel: () => null }));
vi.mock("../../checkin/components/RecentCheckInLog", () => ({
  RecentCheckInLog: () => null,
}));
vi.mock("../../record/components/MergeArchiveSection", () => ({
  MergeArchiveSection: () => null,
}));
vi.mock("../../checkin/components/CheckInMethodSheet", () => ({
  CheckInMethodSheet: () => null,
}));
function dashboard(status: "ACTIVE" | "READY" | "ENDED" = "ACTIVE") {
  const data: ChallengeStatusResponse = {
    challenge: {
      id: 10,
      groupId: 1,
      seqNo: 1,
      status,
      startDate: "2026-09-01",
      endDate: "2026-09-30",
      frequencyType: "DAILY",
      frequencyValue: null,
      daysOfWeek: null,
      dailyCheckInCount: 2,
      allowedTypes: ["PHOTO"],
      requiredDayCount: 30,
      groupCompletedDayCount: 0,
      groupCurrentStreak: 0,
      groupBestStreak: 0,
      ownerId: 1,
    },
    currentDay: 13,
    totalDays: 30,
    participantCount: 4,
    periodProgressRate: 40,
    isCheckInDay: true,
    myCurrentCount: 0,
    myCompleted: false,
    extensionAvailable: false,
  };
  return (
    <MemoryRouter>
      <ChallengeDashboard
        data={data}
        mapType="GYM"
        members={[1, 2, 3, 4].map((userId) => ({
          userId,
          nickname: `멤버${userId}`,
          todayCheckInCount: userId === 4 ? 2 : 0,
          extensionChoice: "PENDING",
        }))}
        characters={[1, 2, 3, 4].map((userId) => ({
          userId,
          nickname: `멤버${userId}`,
          pose: "DEFAULT",
          slots: { HEAD: "hat.webp", TOP: null, BOTTOM: null, SHOES: null },
        }))}
        isCurrent
        currentKnown
        currentUserId={1}
        onExtensionSaved={() => {}}
      />
    </MemoryRouter>
  );
}
const select = (id: number) =>
  screen.getByRole("button", { name: `멤버${id} 콕 찌르기 대상 선택` });
beforeEach(() => {
  vi.resetAllMocks();
  // jsdom does not implement native dialog methods.
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
});
describe("콕 찌르기", () => {
  it("selects members, preserves layers and prevents duplicate requests", async () => {
    let resolve!: () => void;
    vi.mocked(nudgeMember).mockReturnValue(
      new Promise<void>((done) => {
        resolve = done;
      }),
    );
    render(dashboard());
    expect(
      screen.queryByRole("button", { name: "찌르기" }),
    ).not.toBeInTheDocument();
    expect(select(1)).toBeDisabled();
    expect(select(4)).toBeEnabled();
    expect(
      screen.getByRole("img", { name: "멤버2 캐릭터" }).querySelectorAll("img"),
    ).toHaveLength(2);
    fireEvent.click(select(2));
    expect(select(2)).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("dialog")).toHaveTextContent("멤버2님에게");
    expect(nudgeMember).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    fireEvent.click(select(3));
    expect(select(2)).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(screen.getByRole("button", { name: "찌르기" }));
    const pending = screen.getByRole("button", { name: "처리 중…" });
    expect(pending).toBeDisabled();
    fireEvent.click(pending);
    expect(nudgeMember).toHaveBeenCalledExactlyOnceWith(10, 3);
    resolve();
    await waitFor(() =>
      expect(showToast).toHaveBeenCalledWith("멤버3님을 콕 찔렀어요!"),
    );
    expect(
      screen.queryByRole("button", { name: "찌르기" }),
    ).not.toBeInTheDocument();
  });
  it("opens a confirmation for completed members and cancels without sending", () => {
    render(dashboard());
    fireEvent.click(select(4));
    expect(screen.getByRole("dialog")).toHaveTextContent("멤버4님에게");
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(nudgeMember).not.toHaveBeenCalled();
  });
  it.each(["READY", "ENDED"] as const)("disables selection in %s", (status) => {
    render(dashboard(status));
    expect(select(2)).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "찌르기" }),
    ).not.toBeInTheDocument();
  });
  it.each([
    ["CANNOT_NUDGE_SELF", "자기 자신은 콕 찌를 수 없어요."],
    ["ALREADY_CHECKED_IN", "오늘 인증을 이미 완료한 멤버예요."],
    ["ALREADY_NUDGED", "오늘 이미 콕 찌르기를 받은 멤버예요."],
    ["CHALLENGE_NOT_MEMBER", "현재 챌린지에 참여 중인 멤버가 아니에요."],
    ["CHALLENGE_NOT_ACTIVE", "진행 중인 챌린지에서만 사용할 수 있어요."],
    ["UNKNOWN", "서버 오류 메시지"],
  ])("handles %s", async (code, message) => {
    vi.mocked(nudgeMember).mockRejectedValue(
      new ApiError(400, { code, message: "서버 오류 메시지", errors: [] }),
    );
    render(dashboard());
    fireEvent.click(select(2));
    fireEvent.click(screen.getByRole("button", { name: "찌르기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "찌르기" })).toBeEnabled();
  });
});

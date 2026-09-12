import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ParticipatedChallengesPage } from "./ParticipatedChallengesPage";

vi.mock("../api", () => ({
  getMyChallenges: vi.fn(),
  getMyChallengeCovers: vi.fn(),
}));
const { getMyChallenges, getMyChallengeCovers } = await import("../api");

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getMyChallengeCovers).mockResolvedValue([]);
});

describe("ParticipatedChallengesPage", () => {
  it("links to 전체 인증 and to each challenge's album with a cover", async () => {
    vi.mocked(getMyChallenges).mockResolvedValue([
      { challengeId: 1, name: "오운완" },
      { challengeId: 2, name: "매일 독서 30분" },
    ]);
    // 커버는 카드마다 따로 받는다.
    vi.mocked(getMyChallengeCovers).mockImplementation(async (challengeId) =>
      challengeId === 1 ? ["a.jpg", "b.jpg"] : [],
    );

    render(
      <MemoryRouter>
        <ParticipatedChallengesPage />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("link", { name: /전체 인증 모아보기/ }),
    ).toHaveAttribute("href", "/profile/check-ins");

    const album = await screen.findByRole("link", { name: /오운완/ });
    expect(album).toHaveAttribute("href", "/profile/challenges/1/album");

    // 커버 2장 + 빈 칸 2개 (항상 4칸)
    await waitFor(() =>
      expect(
        screen.getByRole("link", { name: /오운완/ }).querySelectorAll("img"),
      ).toHaveLength(2),
    );

    const other = screen.getByRole("link", { name: /매일 독서 30분/ });
    expect(other).toHaveAttribute("href", "/profile/challenges/2/album");
    expect(other.querySelectorAll("img")).toHaveLength(0);

    expect(getMyChallengeCovers).toHaveBeenCalledWith(1);
    expect(getMyChallengeCovers).toHaveBeenCalledWith(2);
  });

  it("shows an empty state when there are no challenges", async () => {
    vi.mocked(getMyChallenges).mockResolvedValue([]);
    render(
      <MemoryRouter>
        <ParticipatedChallengesPage />
      </MemoryRouter>,
    );
    expect(
      await screen.findByText("참여한 챌린지가 없어요"),
    ).toBeInTheDocument();
    expect(getMyChallengeCovers).not.toHaveBeenCalled();
  });
});

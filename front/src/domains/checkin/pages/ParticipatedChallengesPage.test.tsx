import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ParticipatedChallengesPage } from "./ParticipatedChallengesPage";

vi.mock("../api", () => ({ getMyChallenges: vi.fn() }));
const { getMyChallenges } = await import("../api");

beforeEach(() => {
  vi.clearAllMocks();
});

describe("ParticipatedChallengesPage", () => {
  it("links to 전체 인증 and to each challenge's album with a cover", async () => {
    vi.mocked(getMyChallenges).mockResolvedValue([
      { challengeId: 1, name: "오운완", recentMediaUrls: ["a.jpg", "b.jpg"] },
      { challengeId: 2, name: "매일 독서 30분", recentMediaUrls: [] },
    ]);

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
    expect(album.querySelectorAll("img")).toHaveLength(2);

    expect(
      screen.getByRole("link", { name: /매일 독서 30분/ }),
    ).toHaveAttribute("href", "/profile/challenges/2/album");
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
  });
});

import { render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthedVideo } from "./AuthedVideo";

vi.mock("../api/client", () => ({
  apiFetchBlob: vi.fn(),
}));

const { apiFetchBlob } = await import("../api/client");

// jsdom 에는 objectURL 이 없다.
beforeEach(() => {
  vi.clearAllMocks();
  vi.stubGlobal("URL", {
    ...URL,
    createObjectURL: vi.fn(() => "blob:fake-1"),
    revokeObjectURL: vi.fn(),
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AuthedVideo", () => {
  it("외부 절대 URL 은 fetch 없이 그대로 그린다", () => {
    const { container } = render(<AuthedVideo src="https://cdn/x.mp4" />);
    expect(container.querySelector("video")).toHaveAttribute(
      "src",
      "https://cdn/x.mp4",
    );
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });

  it("인증 경로(/api/...)는 Blob 을 받아 objectURL 로 바꿔 그린다", async () => {
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(["x"]));

    const { container } = render(<AuthedVideo src="/api/check-ins/1/media" />);

    await waitFor(() =>
      expect(container.querySelector("video")).toHaveAttribute(
        "src",
        "blob:fake-1",
      ),
    );
    expect(apiFetchBlob).toHaveBeenCalledWith("/api/check-ins/1/media");
  });

  it("로딩 중(objectURL 없음)이면 아무것도 그리지 않는다", () => {
    vi.mocked(apiFetchBlob).mockReturnValue(new Promise(() => {})); // 영원히 pending
    const { container } = render(<AuthedVideo src="/api/check-ins/1/media" />);
    expect(container.querySelector("video")).toBeNull();
  });

  it("src 가 없으면 아무것도 그리지 않는다", () => {
    const { container } = render(<AuthedVideo src={null} />);
    expect(container.querySelector("video")).toBeNull();
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });

  it("나머지 video 속성(autoPlay 등)을 그대로 전달한다", () => {
    const { container } = render(
      <AuthedVideo src="https://cdn/x.mp4" autoPlay loop muted />,
    );
    const video = container.querySelector("video");
    expect(video).toHaveProperty("autoplay", true);
    expect(video).toHaveProperty("loop", true);
    expect(video).toHaveProperty("muted", true);
  });
});

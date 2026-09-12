import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthedImage } from "./AuthedImage";

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
  vi.stubGlobal(
    "IntersectionObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AuthedImage", () => {
  it("외부 절대 URL 은 fetch 없이 그대로 그린다", () => {
    render(<AuthedImage src="https://cdn/x.jpg" alt="사진" />);
    expect(screen.getByRole("img", { name: "사진" })).toHaveAttribute(
      "src",
      "https://cdn/x.jpg",
    );
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });

  it("인증 경로(/api/...)는 Blob 을 받아 objectURL 로 바꿔 그린다", async () => {
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(["x"]));

    render(
      <AuthedImage src="/api/check-ins/1/media" alt="사진" lazy={false} />,
    );

    await waitFor(() =>
      expect(screen.getByRole("img", { name: "사진" })).toHaveAttribute(
        "src",
        "blob:fake-1",
      ),
    );
    expect(apiFetchBlob).toHaveBeenCalledWith("/api/check-ins/1/media");
  });

  it("lazy 면 뷰포트 밖에서는 fetch 하지 않는다", () => {
    vi.mocked(apiFetchBlob).mockResolvedValue(new Blob(["x"]));
    render(<AuthedImage src="/api/check-ins/1/media" alt="사진" />);
    expect(apiFetchBlob).not.toHaveBeenCalled();
  });
});

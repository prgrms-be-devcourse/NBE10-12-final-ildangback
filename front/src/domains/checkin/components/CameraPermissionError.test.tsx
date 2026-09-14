import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { CameraPermissionError } from "./CameraPermissionError";

describe("CameraPermissionError", () => {
  it("제목/안내문구를 그리고, 누르면 onRetry 가 불린다", () => {
    const onRetry = vi.fn();
    render(
      <CameraPermissionError
        title="카메라 권한이 필요해요"
        description="브라우저 설정에서 허용해 주세요."
        onRetry={onRetry}
      />,
    );

    expect(screen.getByText("카메라 권한이 필요해요")).toBeInTheDocument();
    expect(
      screen.getByText("브라우저 설정에서 허용해 주세요."),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it("onRetry 가 없으면(기기 미지원) 재시도 버튼을 그리지 않는다", () => {
    render(
      <CameraPermissionError
        title="이 기기에서는 영상 녹화를 지원하지 않아요"
        description="대신 사진으로 인증해 주세요."
      />,
    );

    expect(
      screen.queryByRole("button", { name: "다시 시도" }),
    ).not.toBeInTheDocument();
  });
});

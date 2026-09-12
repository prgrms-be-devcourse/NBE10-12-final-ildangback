import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { TextField } from "./TextField";

describe("TextField", () => {
  it("associates the visible label with the input by default", () => {
    render(<TextField label="닉네임" />);
    const input = screen.getByLabelText("닉네임");
    expect(input).toBeInTheDocument();
    expect(screen.getByText("닉네임")).not.toHaveClass("sr-only");
  });

  it("keeps the label accessible but visually hidden with hideLabel", () => {
    render(<TextField label="메모" hideLabel placeholder="한 줄 남기기" />);
    // 접근성 이름은 그대로 — 라벨로 인풋을 찾을 수 있어야 한다.
    expect(screen.getByLabelText("메모")).toBeInTheDocument();
    // 다만 시각적으로는 숨겨진다.
    expect(screen.getByText("메모")).toHaveClass("sr-only");
  });
});

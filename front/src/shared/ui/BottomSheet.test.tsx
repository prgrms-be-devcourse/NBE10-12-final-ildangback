import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { BottomSheet } from "./BottomSheet";

function setup(isOpen: boolean) {
  const onClose = vi.fn();
  render(
    <BottomSheet isOpen={isOpen} onClose={onClose} title="오늘 인증하기">
      <p>sheet body</p>
    </BottomSheet>,
  );
  return { onClose };
}

describe("BottomSheet", () => {
  it("renders nothing when closed", () => {
    setup(false);
    expect(screen.queryByText("sheet body")).not.toBeInTheDocument();
  });

  it("renders the title and children when open", () => {
    setup(true);
    expect(screen.getByText("sheet body")).toBeInTheDocument();
    expect(
      screen.getByRole("dialog", { name: "오늘 인증하기" }),
    ).toBeInTheDocument();
  });

  it("closes on backdrop click", async () => {
    const { onClose } = setup(true);
    await userEvent.click(screen.getByTestId("bottomsheet-backdrop"));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes on Escape", async () => {
    const { onClose } = setup(true);
    await userEvent.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes when the grabber handle is activated", async () => {
    const { onClose } = setup(true);
    await userEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("locks body scroll while open and restores it on close", () => {
    const { rerender } = render(
      <BottomSheet isOpen onClose={() => {}}>
        <p>x</p>
      </BottomSheet>,
    );
    expect(document.body.style.overflow).toBe("hidden");
    rerender(
      <BottomSheet isOpen={false} onClose={() => {}}>
        <p>x</p>
      </BottomSheet>,
    );
    expect(document.body.style.overflow).toBe("");
  });
});

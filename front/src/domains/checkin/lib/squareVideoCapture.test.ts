import { describe, expect, it } from "vitest";
import {
  extensionForMimeType,
  pickRecorderMimeType,
} from "./squareVideoCapture";

describe("pickRecorderMimeType", () => {
  it("prefers vp9 webm when the browser supports it", () => {
    const supported = new Set([
      "video/webm;codecs=vp9",
      "video/webm",
      "video/mp4",
    ]);
    expect(pickRecorderMimeType((t) => supported.has(t))).toBe(
      "video/webm;codecs=vp9",
    );
  });

  it("falls back to vp8 webm when vp9 isn't supported", () => {
    const supported = new Set(["video/webm;codecs=vp8", "video/webm"]);
    expect(pickRecorderMimeType((t) => supported.has(t))).toBe(
      "video/webm;codecs=vp8",
    );
  });

  it("falls back to plain webm when no codec param is supported", () => {
    const supported = new Set(["video/webm"]);
    expect(pickRecorderMimeType((t) => supported.has(t))).toBe("video/webm");
  });

  it("falls back to mp4 on Safari (no webm support at all)", () => {
    const supported = new Set(["video/mp4"]);
    expect(pickRecorderMimeType((t) => supported.has(t))).toBe("video/mp4");
  });

  it("throws when nothing is supported", () => {
    expect(() => pickRecorderMimeType(() => false)).toThrow(
      /지원하지 않습니다/,
    );
  });
});

describe("extensionForMimeType", () => {
  it("maps webm (with codec params) to .webm", () => {
    expect(extensionForMimeType("video/webm;codecs=vp9")).toBe("webm");
  });

  it("maps mp4 to .mp4", () => {
    expect(extensionForMimeType("video/mp4")).toBe("mp4");
  });

  it("maps quicktime to .mov", () => {
    expect(extensionForMimeType("video/quicktime")).toBe("mov");
  });

  it("maps jpeg to .jpg", () => {
    expect(extensionForMimeType("image/jpeg")).toBe("jpg");
  });
});

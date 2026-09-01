import { describe, expect, it } from "vitest";
import { computeSquareCrop } from "./squareCapture";

describe("computeSquareCrop", () => {
  it("center-crops a landscape frame and downscales to maxEdge", () => {
    expect(
      computeSquareCrop({ width: 4000, height: 3000, maxEdge: 1440 }),
    ).toEqual({ sx: 500, sy: 0, size: 3000, outSize: 1440 });
  });

  it("center-crops a portrait frame, keeping native size when under maxEdge", () => {
    expect(
      computeSquareCrop({ width: 1080, height: 1920, maxEdge: 1440 }),
    ).toEqual({ sx: 0, sy: 420, size: 1080, outSize: 1080 });
  });

  it("leaves an already-square frame at its size when small enough", () => {
    expect(
      computeSquareCrop({ width: 1000, height: 1000, maxEdge: 1440 }),
    ).toEqual({ sx: 0, sy: 0, size: 1000, outSize: 1000 });
  });

  it("floors an odd crop offset", () => {
    expect(
      computeSquareCrop({ width: 1281, height: 1000, maxEdge: 1440 }),
    ).toEqual({ sx: 140, sy: 0, size: 1000, outSize: 1000 });
  });
});

import { describe, expect, it } from "vitest";
import { parseCheckInTypeParam, toCheckInTypeQuery } from "./checkInTypeParam";

describe("toCheckInTypeQuery", () => {
  it("VIDEO 는 ?type=video 쿼리를 만든다", () => {
    expect(toCheckInTypeQuery("VIDEO")).toBe("?type=video");
  });

  it("PHOTO 는 쿼리 없음(빈 문자열)", () => {
    expect(toCheckInTypeQuery("PHOTO")).toBe("");
  });
});

describe("parseCheckInTypeParam", () => {
  it("type=video 면 VIDEO", () => {
    expect(parseCheckInTypeParam(new URLSearchParams("type=video"))).toBe(
      "VIDEO",
    );
  });

  it("쿼리가 없거나 다른 값이면 PHOTO", () => {
    expect(parseCheckInTypeParam(new URLSearchParams())).toBe("PHOTO");
    expect(parseCheckInTypeParam(new URLSearchParams("type=photo"))).toBe(
      "PHOTO",
    );
  });
});

import { describe, expect, it } from "vitest";
import {
  isSupportedCheckInType,
  toggleAllowedType,
  toSupportedTypesOrDefault,
} from "./types";

describe("isSupportedCheckInType", () => {
  it("PHOTO/VIDEO 는 true", () => {
    expect(isSupportedCheckInType("PHOTO")).toBe(true);
    expect(isSupportedCheckInType("VIDEO")).toBe(true);
  });

  it("LIVE 는 false — 아직 별도 서브시스템", () => {
    expect(isSupportedCheckInType("LIVE")).toBe(false);
  });
});

describe("toggleAllowedType", () => {
  it("없던 값을 넣으면 추가한다", () => {
    expect(toggleAllowedType(["PHOTO"], "VIDEO")).toEqual(["PHOTO", "VIDEO"]);
  });

  it("이미 있는 값을 넣으면 제거한다", () => {
    expect(toggleAllowedType(["PHOTO", "VIDEO"], "PHOTO")).toEqual(["VIDEO"]);
  });

  it("마지막 하나를 빼면 빈 배열이 된다 — 폼 검증(min 1)이 따로 막는다", () => {
    expect(toggleAllowedType(["PHOTO"], "PHOTO")).toEqual([]);
  });

  it("원본 배열을 변형하지 않는다", () => {
    const original = ["PHOTO"] as const;
    toggleAllowedType([...original], "VIDEO");
    expect(original).toEqual(["PHOTO"]);
  });
});

describe("toSupportedTypesOrDefault", () => {
  it("PHOTO/VIDEO 는 그대로 통과한다", () => {
    expect(toSupportedTypesOrDefault(["PHOTO"])).toEqual(["PHOTO"]);
    expect(toSupportedTypesOrDefault(["VIDEO"])).toEqual(["VIDEO"]);
  });

  it("둘 다 있으면 하나로 좁히지 않고 그대로 유지한다", () => {
    expect(toSupportedTypesOrDefault(["PHOTO", "VIDEO"])).toEqual([
      "PHOTO",
      "VIDEO",
    ]);
  });

  it("LIVE 는 걸러낸다", () => {
    expect(toSupportedTypesOrDefault(["PHOTO", "LIVE"])).toEqual(["PHOTO"]);
  });

  it("PHOTO/VIDEO 가 하나도 없으면(LIVE 만 있거나 빈 배열) PHOTO 로 되돌린다", () => {
    expect(toSupportedTypesOrDefault(["LIVE"])).toEqual(["PHOTO"]);
    expect(toSupportedTypesOrDefault([])).toEqual(["PHOTO"]);
  });
});

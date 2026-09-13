import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AuthContext, type AuthContextValue } from "../../app/auth-context";
import { tokenStore } from "../../shared/api/tokenStore";
import type { UserRole } from "../../shared/api/types";
import { RequireAdmin } from "./RequireAdmin";

/** role 클레임만 든 가짜 AT. 서명은 안 보므로 헤더와 서명은 아무 값이어도 된다. */
function tokenWithRole(role: UserRole): string {
  const payload = btoa(JSON.stringify({ sub: "1", role }))
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
  return `header.${payload}.signature`;
}

function renderGuard(
  value: Partial<AuthContextValue> = {},
  token: string | null = null,
) {
  vi.spyOn(tokenStore, "getAccessToken").mockReturnValue(token);

  const auth = {
    status: "authenticated",
    user: null,
    signIn: async () => {},
    signOut: async () => {},
    replaceUser: () => {},
    ...value,
  } as AuthContextValue;

  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={["/admin"]}>
        <Routes>
          <Route path="/admin" element={<RequireAdmin />}>
            <Route index element={<p>관리자 화면</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

beforeEach(() => {
  vi.restoreAllMocks();
});

describe("RequireAdmin", () => {
  it("AT 의 role 이 ADMIN 이면 자식 화면을 그린다", () => {
    renderGuard({}, tokenWithRole("ADMIN"));
    expect(screen.getByText("관리자 화면")).toBeInTheDocument();
  });

  it("USER 면 404 를 보여준다 - 관리자 전용이라고 알려주지 않는다", () => {
    renderGuard({}, tokenWithRole("USER"));
    expect(screen.queryByText("관리자 화면")).not.toBeInTheDocument();
    expect(screen.getByText("404")).toBeInTheDocument();
    // 경로의 존재를 확인해 주지 않는다.
    expect(screen.queryByText(/관리자/)).not.toBeInTheDocument();
  });

  it("AT 가 없으면 404 를 보여준다", () => {
    renderGuard({}, null);
    expect(screen.getByText("404")).toBeInTheDocument();
  });

  it("토큰 모양이 깨져 있으면 404 를 보여준다", () => {
    renderGuard({}, "not-a-jwt");
    expect(screen.getByText("404")).toBeInTheDocument();
  });

  it("복구 중이면 자식도 404 도 그리지 않는다", () => {
    renderGuard({ status: "loading" }, tokenWithRole("ADMIN"));
    expect(screen.queryByText("관리자 화면")).not.toBeInTheDocument();
    expect(screen.queryByText("404")).not.toBeInTheDocument();
  });
});

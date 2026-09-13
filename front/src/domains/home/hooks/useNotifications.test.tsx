import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { getNotifications, readNotification } from "../api";
import { useNotifications } from "./useNotifications";
vi.mock("../api", () => ({
  getNotifications: vi.fn(),
  readNotification: vi.fn(),
}));
const item = {
  id: 1,
  type: "CHECK_IN_NUDGE",
  title: "알림",
  body: "본문",
  refId: 106,
  readAt: null,
  createdAt: "2026-09-13T15:00:10",
};
beforeEach(() => vi.resetAllMocks());
it("loads notifications and updates only after PATCH, without refetching", async () => {
  vi.mocked(getNotifications).mockResolvedValue([item]);
  let done!: () => void;
  vi.mocked(readNotification).mockReturnValue(
    new Promise<void>((resolve) => {
      done = resolve;
    }),
  );
  const { result } = renderHook(() => useNotifications());
  await waitFor(() => expect(result.current.loading).toBe(false));
  let pending!: Promise<boolean>;
  act(() => {
    pending = result.current.markRead(item);
  });
  expect(result.current.notifications[0].readAt).toBeNull();
  await act(async () => {
    expect(await result.current.markRead(item)).toBe(false);
  });
  expect(readNotification).toHaveBeenCalledTimes(1);
  await act(async () => {
    done();
    await pending;
  });
  expect(result.current.notifications).toEqual([]);
  expect(result.current.unreadCount).toBe(0);
  expect(getNotifications).toHaveBeenCalledTimes(1);
  vi.mocked(getNotifications).mockResolvedValue([]);
  await act(async () => {
    await result.current.reload();
  });
  expect(result.current.notifications).toEqual([]);
  expect(result.current.unreadCount).toBe(0);
});
it("keeps unread state and prevents navigation on failure", async () => {
  vi.mocked(getNotifications).mockResolvedValue([item]);
  vi.mocked(readNotification).mockRejectedValue(new Error("offline"));
  const { result } = renderHook(() => useNotifications());
  await waitFor(() => expect(result.current.loading).toBe(false));
  await act(async () => {
    expect(await result.current.markRead(item)).toBe(false);
  });
  expect(result.current.notifications[0].readAt).toBeNull();
  expect(result.current.error).toContain("네트워크");
});
it("exposes load errors and can retry an empty list", async () => {
  vi.mocked(getNotifications)
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValue([]);
  const { result } = renderHook(() => useNotifications());
  await waitFor(() => expect(result.current.error).toBeTruthy());
  await act(async () => {
    await result.current.reload();
  });
  expect(result.current.notifications).toEqual([]);
  expect(result.current.unreadCount).toBe(0);
  expect(result.current.error).toBeNull();
});

it("does not let a GET started before PATCH restore the removed card", async () => {
  vi.mocked(getNotifications).mockResolvedValueOnce([item]);
  vi.mocked(readNotification).mockResolvedValue(undefined);
  const { result } = renderHook(() => useNotifications());
  await waitFor(() => expect(result.current.unreadCount).toBe(1));
  let finishGet!: (items: (typeof item)[]) => void;
  vi.mocked(getNotifications).mockReturnValueOnce(
    new Promise((resolve) => {
      finishGet = resolve;
    }),
  );
  let reload!: Promise<void>;
  act(() => {
    reload = result.current.reload();
  });
  await act(async () => {
    await result.current.markRead(item);
  });
  expect(result.current.unreadCount).toBe(0);
  await act(async () => {
    finishGet([item]);
    await reload;
  });
  expect(result.current.notifications).toEqual([]);
  expect(result.current.unreadCount).toBe(0);
});

it("excludes server-confirmed read items from both cards and badge on every GET", async () => {
  vi.mocked(getNotifications).mockResolvedValue([
    item,
    { ...item, id: 2, readAt: "2026-09-13T16:00:00" },
  ]);
  const { result } = renderHook(() => useNotifications());
  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.notifications).toEqual([item]);
  expect(result.current.unreadCount).toBe(1);
  vi.mocked(getNotifications).mockResolvedValue([
    { ...item, readAt: "2026-09-13T16:00:00" },
  ]);
  await act(async () => {
    await result.current.reload();
  });
  expect(result.current.notifications).toEqual([]);
  expect(result.current.unreadCount).toBe(0);
});

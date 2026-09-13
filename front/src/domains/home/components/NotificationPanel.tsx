import { XIcon } from "@phosphor-icons/react";
import { useLayoutEffect, useRef, type RefObject } from "react";

import type { NotificationResponse } from "../api";
import { formatDateTimeMinute } from "../../../shared/lib/date";
import pokeNotification from "../../../assets/icons/poke-notification-hand.webp";
import { FormAlert } from "../../../shared/ui/FormAlert";

export function NotificationPanel({
  anchorRef,
  notifications,
  onClose,
  loading,
  error,
  pendingId,
  onRetry,
  onSelect,
}: {
  anchorRef: RefObject<HTMLButtonElement | null>;
  notifications: NotificationResponse[];
  loading: boolean;
  error: string | null;
  pendingId: number | null;
  onRetry: () => void;
  onSelect: (notification: NotificationResponse) => Promise<void>;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useLayoutEffect(() => {
    const dialog = ref.current;
    const previouslyFocused = document.activeElement;
    if (!dialog) return;
    const position = () => {
      const anchor = anchorRef.current?.getBoundingClientRect();
      const width = Math.min(430, window.innerWidth - 24);
      const left = Math.max(
        12,
        Math.min(
          window.innerWidth - width - 12,
          (anchor?.right ?? window.innerWidth - 24) + 10 - width,
        ),
      );
      const top = Math.max(
        12,
        Math.min((anchor?.bottom ?? 60) + 12, window.innerHeight - 160),
      );
      dialog.style.width = `${width}px`;
      dialog.style.left = `${left}px`;
      dialog.style.top = `${top}px`;
      dialog.style.setProperty(
        "--notification-max-height",
        `min(72dvh, ${Math.max(120, window.innerHeight - top - 16)}px)`,
      );
      dialog.style.setProperty(
        "--bell-offset",
        `${Math.max(20, Math.min(width - 20, (anchor ? anchor.left + anchor.width / 2 : left + width - 30) - left))}px`,
      );
    };
    position();
    if (!dialog.open) dialog.showModal();
    window.addEventListener("resize", position);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      window.removeEventListener("resize", position);
      document.body.style.overflow = previousOverflow;
      dialog.close();
      if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
    };
  }, [anchorRef]);

  return (
    <dialog
      ref={ref}
      id="home-notifications"
      aria-labelledby="home-notifications-title"
      onCancel={(event) => {
        if (pendingId !== null) event.preventDefault();
        else onClose();
      }}
      onClick={(event) => {
        if (event.target === event.currentTarget && pendingId === null)
          onClose();
      }}
      className="fixed right-auto bottom-auto m-0 max-h-none max-w-none overflow-visible rounded-2xl border border-purple-200 bg-white p-0 shadow-xl backdrop:bg-purple-950/20"
    >
      <span
        aria-hidden
        className="pointer-events-none absolute -top-2 left-[var(--bell-offset)] h-4 w-4 -translate-x-1/2 rotate-45 border-t border-l border-purple-200 bg-white"
      />
      <div className="relative flex max-h-[var(--notification-max-height)] min-h-0 flex-col overflow-hidden rounded-2xl">
        <header className="flex shrink-0 items-center justify-between border-b border-purple-100 bg-white px-5 py-4">
          <h2
            id="home-notifications-title"
            className="text-lg font-bold text-purple-900"
          >
            알림
          </h2>
          <button
            type="button"
            disabled={pendingId !== null}
            onClick={onClose}
            aria-label="알림 닫기"
            className="rounded-lg p-2 text-purple-500 hover:bg-purple-50 focus-visible:outline-2 focus-visible:outline-purple-400"
          >
            <XIcon size={20} aria-hidden />
          </button>
        </header>
        <div className="min-h-0 shrink overflow-y-auto overscroll-contain bg-purple-50/60 p-4">
          {error && (
            <div className="mb-3 space-y-2">
              <FormAlert message={error} />
              <button
                type="button"
                onClick={onRetry}
                className="text-sm font-semibold text-purple-700"
              >
                다시 시도
              </button>
            </div>
          )}
          {loading ? (
            <p
              role="status"
              className="flex min-h-32 items-center justify-center text-center text-sm text-gray-500"
            >
              알림을 불러오는 중…
            </p>
          ) : notifications.length === 0 && !error ? (
            <p
              role="status"
              className="flex min-h-32 items-center justify-center text-center text-sm text-gray-500"
            >
              새로운 알림이 없어요.
            </p>
          ) : (
            <ul className="space-y-3">
              {notifications.map((notification) => (
                <li
                  key={notification.id}
                  className={`rounded-xl border ${notification.readAt === null ? "border-purple-200 bg-purple-100/60" : "border-purple-100 bg-white"}`}
                >
                  <button
                    type="button"
                    disabled={pendingId !== null}
                    onClick={() => void onSelect(notification)}
                    aria-busy={pendingId === notification.id}
                    className="block w-full rounded-xl p-4 text-left focus-visible:outline-2 focus-visible:outline-purple-500 disabled:cursor-wait"
                  >
                    <span className="flex items-start gap-2">
                      {notification.type === "CHECK_IN_NUDGE" ? (
                        <img
                          src={pokeNotification}
                          alt=""
                          className="h-5 w-5 shrink-0 object-contain"
                        />
                      ) : notification.readAt === null ? (
                        <span
                          aria-label="읽지 않은 알림"
                          className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-purple-500"
                        />
                      ) : null}
                      {notification.type === "CHECK_IN_NUDGE" &&
                        notification.readAt === null && (
                          <span className="sr-only">읽지 않은 알림</span>
                        )}
                      <p className="text-sm font-semibold text-gray-900 wrap-anywhere">
                        {notification.title}
                      </p>
                    </span>
                    <p className="mt-2 text-sm leading-relaxed whitespace-pre-line text-gray-600 wrap-anywhere">
                      {notification.body}
                    </p>
                    <time
                      dateTime={notification.createdAt}
                      className="mt-3 block text-xs text-gray-500"
                    >
                      {formatDateTimeMinute(notification.createdAt)}
                    </time>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </dialog>
  );
}

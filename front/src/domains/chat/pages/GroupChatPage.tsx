import {
  CaretLeftIcon,
  PaperPlaneRightIcon,
  SirenIcon,
} from "@phosphor-icons/react";
import type { FormEvent } from "react";
import {
  Fragment,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";
import { useNavigate, useParams } from "react-router";
import { dateKey, formatMonthDay, formatTime } from "../../../shared/lib/date";
import { useAuth } from "../../../shared/lib/useAuth";
import { useToast } from "../../../shared/lib/useToast";
import { getGroupMessages, markMessagesRead } from "../api";
import { ReportDialog } from "../../report/components/ReportDialog";
import { useGroupChatSocket } from "../lib/chatSocket";
import type { ChatMessageResponse } from "../types";

const PAGE_SIZE = 30;

export function GroupChatPage() {
  const { groupId } = useParams();
  const id = Number(groupId);
  if (!Number.isSafeInteger(id) || id <= 0) return null;
  return <GroupChatContent key={id} groupId={id} />;
}

function GroupChatContent({ groupId }: { groupId: number }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const { showToast } = useToast();

  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [cursor, setCursor] = useState<number | null>(null);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState(false);
  const [draft, setDraft] = useState("");
  const [socketError, setSocketError] = useState<string | null>(null);
  const [reportTarget, setReportTarget] = useState<ChatMessageResponse | null>(
    null,
  );

  const listRef = useRef<HTMLDivElement | null>(null);
  const topSentinelRef = useRef<HTMLDivElement | null>(null);
  const prevScrollHeightRef = useRef<number | null>(null);
  const shouldStickToBottomRef = useRef(true);

  useEffect(() => {
    let cancelled = false;
    getGroupMessages(groupId, { size: PAGE_SIZE })
      .then((page) => {
        if (cancelled) return;
        setMessages([...page.content].reverse());
        setCursor(page.nextCursor);
        setHasNext(page.hasNext);
        if (page.content[0]) {
          markMessagesRead(groupId, page.content[0].messageId).catch(
            () => undefined,
          );
        }
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [groupId]);

  const handleIncoming = useCallback(
    (message: ChatMessageResponse) => {
      shouldStickToBottomRef.current = isNearBottom(listRef.current);
      setMessages((prev) => [...prev, message]);
      if (shouldStickToBottomRef.current) {
        markMessagesRead(groupId, message.messageId).catch(() => undefined);
      }
    },
    [groupId],
  );

  const handleSocketError = useCallback((message: string) => {
    setSocketError(message);
  }, []);

  const { connected, sendMessage } = useGroupChatSocket(
    groupId,
    handleIncoming,
    handleSocketError,
  );

  // 처음 불러온 뒤와 새 메시지가 오면 바닥에 붙인다. 위 이력을 불러온 경우엔
  // 보던 위치가 안 튀도록 늘어난 높이만큼 스크롤을 보정한다.
  useLayoutEffect(() => {
    const container = listRef.current;
    if (loading || !container) return;

    if (prevScrollHeightRef.current != null) {
      container.scrollTop =
        container.scrollHeight - prevScrollHeightRef.current;
      prevScrollHeightRef.current = null;
      return;
    }
    if (shouldStickToBottomRef.current) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages, loading]);

  const loadOlder = useCallback(async () => {
    if (loadingOlder || cursor == null || !hasNext) return;
    setLoadingOlder(true);
    prevScrollHeightRef.current = listRef.current?.scrollHeight ?? null;
    try {
      const page = await getGroupMessages(groupId, {
        cursor,
        size: PAGE_SIZE,
      });
      setMessages((prev) => [...[...page.content].reverse(), ...prev]);
      setCursor(page.nextCursor);
      setHasNext(page.hasNext);
    } catch {
      prevScrollHeightRef.current = null;
    } finally {
      setLoadingOlder(false);
    }
  }, [cursor, groupId, hasNext, loadingOlder]);

  useEffect(() => {
    const sentinel = topSentinelRef.current;
    if (!sentinel || !hasNext || loading) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadOlder();
      },
      { root: listRef.current, rootMargin: "100px" },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasNext, loading, loadOlder]);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    const content = draft.trim();
    if (!content || !connected) return;
    shouldStickToBottomRef.current = true;
    sendMessage(content);
    setDraft("");
  };

  return (
    <div className="flex h-dvh flex-col">
      <header className="relative flex h-14 shrink-0 items-center border-b border-purple-100 px-4">
        <button
          type="button"
          onClick={() => navigate(-1)}
          aria-label="뒤로 가기"
          className="-ml-2 rounded-lg p-2 text-gray-900 hover:bg-purple-50"
        >
          <CaretLeftIcon size={24} weight="bold" />
        </button>
        <p className="pointer-events-none absolute inset-x-0 flex justify-center text-[16px] font-bold text-gray-900">
          그룹 채팅
        </p>
        {!connected && !loading && (
          <span className="absolute right-4 text-[11px] text-gray-400">
            연결 중…
          </span>
        )}
      </header>

      <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
        <div ref={topSentinelRef} />
        {loadingOlder && (
          <p className="py-1 text-center text-[11px] text-gray-400">
            이전 메시지를 불러오는 중…
          </p>
        )}
        {loading && (
          <p className="py-10 text-center text-sm text-gray-500">
            대화를 불러오는 중…
          </p>
        )}
        {!loading && error && (
          <p className="py-10 text-center text-sm text-gray-500">
            대화를 불러오지 못했어요.
          </p>
        )}
        {!loading && !error && messages.length === 0 && (
          <p className="py-10 text-center text-sm text-gray-500">
            아직 대화가 없어요. 첫 메시지를 남겨보세요.
          </p>
        )}
        {!loading &&
          messages.map((message, index) => {
            const prev = messages[index - 1];
            const showDateDivider =
              !prev || dateKey(prev.createdAt) !== dateKey(message.createdAt);
            return (
              <Fragment key={message.messageId}>
                {showDateDivider && (
                  <DateDivider createdAt={message.createdAt} />
                )}
                <ChatBubble
                  message={message}
                  mine={message.senderId === user?.id}
                  onReport={
                    message.messageType === "TEXT" &&
                    message.senderId !== null &&
                    message.senderId !== user?.id
                      ? () => setReportTarget(message)
                      : undefined
                  }
                />
              </Fragment>
            );
          })}
      </div>

      <ReportDialog
        isOpen={reportTarget !== null}
        onClose={() => setReportTarget(null)}
        onSubmitted={() => showToast("신고를 접수했어요.")}
        targets={
          reportTarget
            ? [
                {
                  targetType: "CHAT_MESSAGE",
                  targetId: reportTarget.messageId,
                  label: `${reportTarget.senderNickname} 님의 이 메시지`,
                },
              ]
            : []
        }
      />

      {socketError && (
        <p className="px-4 pb-1 text-center text-[11px] text-red-500">
          {socketError}
        </p>
      )}

      <form
        onSubmit={handleSubmit}
        className="flex shrink-0 items-center gap-2 border-t border-purple-100 p-3"
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          maxLength={1000}
          placeholder={connected ? "메시지 보내기" : "연결하는 중이에요…"}
          disabled={!connected}
          className="flex-1 rounded-full border border-purple-200 px-4 py-2.5 text-sm outline-none focus:border-purple-400 disabled:bg-gray-50"
        />
        <button
          type="submit"
          disabled={!connected || !draft.trim()}
          aria-label="보내기"
          className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-purple-600 text-white disabled:opacity-40"
        >
          <PaperPlaneRightIcon size={18} weight="fill" />
        </button>
      </form>
    </div>
  );
}

function DateDivider({ createdAt }: { createdAt: string }) {
  return (
    <div className="flex justify-center py-1">
      <span className="rounded-full bg-gray-100 px-3 py-1 text-[11px] font-medium text-gray-500">
        {formatMonthDay(createdAt)}
      </span>
    </div>
  );
}

function ChatBubble({
  message,
  mine,
  onReport,
}: {
  message: ChatMessageResponse;
  mine: boolean;
  /** 남의 일반 메시지에만 넘어온다. 시스템 메시지와 내 메시지는 신고 대상이 아니다. */
  onReport?: () => void;
}) {
  if (message.messageType === "SYSTEM") {
    return (
      <p className="py-1 text-center text-[11px] text-gray-400">
        {message.content}
      </p>
    );
  }

  return (
    <div className={`flex flex-col ${mine ? "items-end" : "items-start"}`}>
      {!mine && (
        <span className="mb-1 px-1 text-[11px] text-gray-500">
          {message.senderNickname}
        </span>
      )}
      <div className="flex items-end gap-1.5">
        {mine && (
          <span className="shrink-0 text-[10px] text-gray-400">
            {formatTime(message.createdAt)}
          </span>
        )}
        <p
          className={`max-w-[70vw] rounded-2xl px-3.5 py-2 text-[14px] wrap-anywhere ${
            mine
              ? "rounded-br-sm bg-purple-600 text-white"
              : "rounded-bl-sm bg-purple-50 text-gray-900"
          }`}
        >
          {message.content}
        </p>
        {!mine && (
          <span className="flex shrink-0 items-center gap-1">
            <span className="text-[10px] text-gray-400">
              {formatTime(message.createdAt)}
            </span>
            {onReport && (
              <button
                type="button"
                onClick={onReport}
                aria-label={`${message.senderNickname} 님의 메시지 신고`}
                className="text-gray-300 hover:text-gray-500"
              >
                <SirenIcon size={13} weight="fill" />
              </button>
            )}
          </span>
        )}
      </div>
    </div>
  );
}

function isNearBottom(container: HTMLDivElement | null): boolean {
  if (!container) return true;
  const threshold = 120;
  return (
    container.scrollHeight - container.scrollTop - container.clientHeight <
    threshold
  );
}

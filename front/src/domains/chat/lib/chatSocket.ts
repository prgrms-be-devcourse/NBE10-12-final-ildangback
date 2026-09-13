import { Client, type IMessage } from "@stomp/stompjs";
import { useCallback, useEffect, useRef, useState } from "react";
import { tokenStore } from "../../../shared/api/tokenStore";
import type { ChatMessageResponse } from "../types";

// SockJS 없이 순수 WebSocket 이다 (WebSocketConfig 참고). http(s) 주소를 ws(s) 로 바꿔 쓴다.
function resolveBrokerUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL;
  if (base) return `${base.replace(/^http/, "ws")}/ws`;
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws`;
}

export function useGroupChatSocket(
  groupId: number,
  onMessage: (message: ChatMessageResponse) => void,
  onError: (message: string) => void,
) {
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const onMessageRef = useRef(onMessage);
  const onErrorRef = useRef(onError);
  useEffect(() => {
    onMessageRef.current = onMessage;
    onErrorRef.current = onError;
  });

  useEffect(() => {
    const client = new Client({
      brokerURL: resolveBrokerUrl(),
      connectHeaders: {
        Authorization: `Bearer ${tokenStore.getAccessToken() ?? ""}`,
      },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/groups/${groupId}`, (frame: IMessage) => {
          onMessageRef.current(JSON.parse(frame.body) as ChatMessageResponse);
        });
        client.subscribe("/user/queue/errors", (frame: IMessage) => {
          const body = JSON.parse(frame.body) as { message?: string };
          onErrorRef.current(body.message ?? "메시지를 처리하지 못했어요.");
        });
      },
      onWebSocketClose: () => setConnected(false),
      onStompError: () => onErrorRef.current("채팅 연결에 문제가 생겼어요."),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      clientRef.current = null;
      client.deactivate();
    };
  }, [groupId]);

  const sendMessage = useCallback(
    (content: string) => {
      clientRef.current?.publish({
        destination: `/app/groups/${groupId}/messages`,
        body: JSON.stringify({ content }),
      });
    },
    [groupId],
  );

  return { connected, sendMessage };
}

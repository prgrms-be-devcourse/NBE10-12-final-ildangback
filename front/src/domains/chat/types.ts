export type MessageType = "TEXT" | "SYSTEM";

export interface ChatMessageResponse {
  messageId: number;
  senderId: number | null;
  senderNickname: string | null;
  messageType: MessageType;
  content: string;
  createdAt: string;
}

export interface ChatUnreadCountResponse {
  unreadCount: number;
}

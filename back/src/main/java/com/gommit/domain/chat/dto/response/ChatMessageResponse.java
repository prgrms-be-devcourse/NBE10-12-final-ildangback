package com.gommit.domain.chat.dto.response;

import com.gommit.domain.chat.entity.GroupMessage;
import com.gommit.domain.chat.entity.MessageType;
import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        Long senderId,
        String senderNickname,
        MessageType messageType,
        String content,
        LocalDateTime createdAt) {

    public ChatMessageResponse(GroupMessage message, String senderNickname) {
        this(
                message.getId(),
                message.getSenderId(),
                senderNickname,
                message.getMessageType(),
                message.getContent(),
                message.getCreatedAt());
    }
}

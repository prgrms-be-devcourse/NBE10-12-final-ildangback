package com.gommit.domain.chat.service;

import com.gommit.domain.chat.dto.request.ChatMessageSendRequest;
import com.gommit.domain.chat.dto.response.ChatMessageResponse;
import com.gommit.domain.chat.dto.response.ChatUnreadCountResponse;
import com.gommit.domain.chat.entity.GroupMessage;
import com.gommit.domain.chat.repository.GroupMessageRepository;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final GroupMessageRepository groupMessageRepository;
    private final GroupService groupService;
    private final UserService userService;

    // 그룹 메시지 목록 조회
    @Transactional(readOnly = true)
    public SliceResponse<ChatMessageResponse> getMessages(Long groupId, Long userId, Long cursor, int size) {
        verifyActiveMember(groupId, userId);

        List<GroupMessage> messages = groupMessageRepository.findMessages(groupId, cursor, PageRequest.of(0, size + 1));

        Map<Long, String> nicknames = userService.findNicknames(messages.stream()
                .map(GroupMessage::getSenderId)
                .filter(Objects::nonNull)
                .toList());

        List<ChatMessageResponse> responses = messages.stream()
                .map(message -> new ChatMessageResponse(message, nicknames.get(message.getSenderId())))
                .toList();

        return SliceResponse.ofCursor(responses, size, ChatMessageResponse::messageId);
    }

    // 메시지 전송
    @Transactional
    public ChatMessageResponse sendMessage(Long groupId, Long userId, ChatMessageSendRequest request) {
        verifyActiveMember(groupId, userId);

        GroupMessage message = groupMessageRepository.save(GroupMessage.builder()
                .groupId(groupId)
                .senderId(userId)
                .content(request.content().trim())
                .build());
        groupService.markMessagesRead(groupId, userId, message.getId());

        return new ChatMessageResponse(
                message, userService.findNicknames(List.of(userId)).get(userId));
    }

    // 읽음 커서 갱신
    @Transactional
    public void markRead(Long groupId, Long userId, Long lastReadMessageId) {
        verifyActiveMember(groupId, userId);
        groupService.markMessagesRead(groupId, userId, lastReadMessageId);
    }

    // 내 읽음 커서 이후 쌓인 메시지 수
    @Transactional(readOnly = true)
    public ChatUnreadCountResponse getUnreadCount(Long groupId, Long userId) {
        verifyActiveMember(groupId, userId);
        Long cursor = groupService.findReadCursor(groupId, userId);
        long count = groupMessageRepository.countByGroupIdAndIdGreaterThan(groupId, cursor == null ? 0L : cursor);
        return new ChatUnreadCountResponse(count);
    }

    // ACTIVE 멤버 확인
    private void verifyActiveMember(Long groupId, Long userId) {
        if (!groupService.isActiveMember(groupId, userId)) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }
}

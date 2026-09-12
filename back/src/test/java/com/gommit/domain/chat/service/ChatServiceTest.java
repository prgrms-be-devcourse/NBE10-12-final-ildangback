package com.gommit.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.chat.dto.request.ChatMessageSendRequest;
import com.gommit.domain.chat.dto.response.ChatMessageResponse;
import com.gommit.domain.chat.entity.GroupMessage;
import com.gommit.domain.chat.entity.MessageType;
import com.gommit.domain.chat.repository.GroupMessageRepository;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 10L;

    @Mock
    private GroupMessageRepository groupMessageRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ChatService chatService;

    private GroupMessage message(Long id, Long senderId, String content) {
        GroupMessage message = GroupMessage.builder()
                .groupId(GROUP_ID)
                .senderId(senderId)
                .content(content)
                .build();
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    @Nested
    @DisplayName("메시지 목록 조회")
    class GetMessages {

        @Test
        @DisplayName("ACTIVE 멤버가 아니면 조회할 수 없다")
        void rejectsNonMember() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> chatService.getMessages(GROUP_ID, USER_ID, null, 20))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);

            verify(groupMessageRepository, never()).findMessages(any(), any(), any());
        }

        @Test
        @DisplayName("요청한 크기보다 한 건 더 조회해 다음 페이지 여부를 판단한다")
        void returnsCursorSlice() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupMessageRepository.findMessages(any(), any(), any()))
                    .thenReturn(
                            List.of(message(3L, USER_ID, "셋"), message(2L, USER_ID, "둘"), message(1L, USER_ID, "하나")));
            when(userService.findNicknames(anyCollection())).thenReturn(Map.of(USER_ID, "테스터"));

            SliceResponse<ChatMessageResponse> response = chatService.getMessages(GROUP_ID, USER_ID, null, 2);

            assertThat(response.content()).hasSize(2);
            assertThat(response.hasNext()).isTrue();
            assertThat(response.nextCursor()).isEqualTo(2L);

            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(groupMessageRepository).findMessages(any(), any(), pageable.capture());
            assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("보낸 사람 닉네임을 한 번에 조회해 붙인다")
        void attachesNicknamesInBatch() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupMessageRepository.findMessages(any(), any(), any()))
                    .thenReturn(List.of(message(2L, 20L, "둘"), message(1L, USER_ID, "하나")));
            when(userService.findNicknames(anyCollection())).thenReturn(Map.of(USER_ID, "테스터", 20L, "다른사람"));

            SliceResponse<ChatMessageResponse> response = chatService.getMessages(GROUP_ID, USER_ID, null, 20);

            assertThat(response.content())
                    .extracting(ChatMessageResponse::senderNickname)
                    .containsExactly("다른사람", "테스터");
            verify(userService).findNicknames(anyCollection());
        }
    }

    @Nested
    @DisplayName("메시지 전송")
    class SendMessage {

        @Test
        @DisplayName("ACTIVE 멤버가 아니면 전송할 수 없다")
        void rejectsNonMember() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> chatService.sendMessage(GROUP_ID, USER_ID, new ChatMessageSendRequest("안녕하세요")))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);

            verify(groupMessageRepository, never()).save(any());
        }

        @Test
        @DisplayName("TEXT 메시지로 저장하고 앞뒤 공백을 제거한다")
        void savesTrimmedTextMessage() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(true);
            when(groupMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(userService.findNicknames(anyCollection())).thenReturn(Map.of(USER_ID, "테스터"));

            ChatMessageResponse response =
                    chatService.sendMessage(GROUP_ID, USER_ID, new ChatMessageSendRequest("  안녕하세요  "));

            ArgumentCaptor<GroupMessage> saved = ArgumentCaptor.forClass(GroupMessage.class);
            verify(groupMessageRepository).save(saved.capture());
            assertThat(saved.getValue().getContent()).isEqualTo("안녕하세요");
            assertThat(saved.getValue().getMessageType()).isEqualTo(MessageType.TEXT);
            assertThat(saved.getValue().getSenderId()).isEqualTo(USER_ID);

            assertThat(response.content()).isEqualTo("안녕하세요");
            assertThat(response.senderNickname()).isEqualTo("테스터");
        }
    }
}

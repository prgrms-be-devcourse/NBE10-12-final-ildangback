package com.gommit.domain.chat.controller;

import com.gommit.domain.chat.dto.request.ChatReadRequest;
import com.gommit.domain.chat.dto.response.ChatMessageResponse;
import com.gommit.domain.chat.dto.response.ChatUnreadCountResponse;
import com.gommit.domain.chat.service.ChatService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Group Chat", description = "그룹 채팅 API")
@RestController
@RequestMapping("/api/groups/{groupId}/messages")
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "그룹 메시지 목록 조회")
    @GetMapping
    public ResponseEntity<SliceResponse<ChatMessageResponse>> getMessages(
            @PathVariable Long groupId,
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(chatService.getMessages(groupId, actor.getId(), cursor, size));
    }

    @Operation(summary = "읽음 커서 갱신")
    @PutMapping("/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long groupId, @CurrentUser SecurityUser actor, @Valid @RequestBody ChatReadRequest request) {
        chatService.markRead(groupId, actor.getId(), request.lastReadMessageId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "안 읽은 메시지 수 조회")
    @GetMapping("/unread-count")
    public ResponseEntity<ChatUnreadCountResponse> getUnreadCount(
            @PathVariable Long groupId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(chatService.getUnreadCount(groupId, actor.getId()));
    }
}

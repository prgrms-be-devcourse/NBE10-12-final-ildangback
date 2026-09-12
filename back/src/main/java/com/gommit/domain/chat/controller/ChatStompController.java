package com.gommit.domain.chat.controller;

import com.gommit.domain.chat.dto.request.ChatMessageSendRequest;
import com.gommit.domain.chat.dto.response.ChatMessageResponse;
import com.gommit.domain.chat.service.ChatService;
import com.gommit.global.config.WebSocketConfig;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.exception.ErrorResponse;
import com.gommit.global.security.StompPrincipals;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/groups/{groupId}/messages")
    public void sendMessage(
            @DestinationVariable Long groupId, @Valid @Payload ChatMessageSendRequest request, Principal principal) {
        ChatMessageResponse response =
                chatService.sendMessage(groupId, StompPrincipals.resolveUserId(principal), request);

        messagingTemplate.convertAndSend(WebSocketConfig.GROUP_TOPIC_PREFIX + groupId, response);
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser(destinations = WebSocketConfig.ERROR_QUEUE, broadcast = false)
    public ErrorResponse handleBusinessException(BusinessException e) {
        return ErrorResponse.of(e.getErrorCode());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser(destinations = WebSocketConfig.ERROR_QUEUE, broadcast = false)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        return ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errors);
    }
}

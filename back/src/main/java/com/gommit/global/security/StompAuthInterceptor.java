package com.gommit.global.security;

import com.gommit.domain.group.service.GroupService;
import com.gommit.global.config.WebSocketConfig;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final GroupService groupService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            verifySubscription(accessor);
        }

        return message;
    }

    // 접속 인증
    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        SecurityUser user = token.isEmpty() ? null : jwtProvider.parse(token);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    // 구독 권한 확인
    private void verifySubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (WebSocketConfig.ERROR_QUEUE_SUBSCRIPTION.equals(destination)) {
            return;
        }
        if (destination == null || !destination.startsWith(WebSocketConfig.GROUP_TOPIC_PREFIX)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        Long groupId = parseGroupId(destination.substring(WebSocketConfig.GROUP_TOPIC_PREFIX.length()));
        if (!groupService.isActiveMember(groupId, resolveUserId(accessor))) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    // 구독 주소에서 그룹 번호 추출
    private Long parseGroupId(String segment) {
        try {
            return Long.valueOf(segment);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    // 인증된 사용자 번호 추출
    private Long resolveUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof SecurityUser user) {
            return user.getId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}

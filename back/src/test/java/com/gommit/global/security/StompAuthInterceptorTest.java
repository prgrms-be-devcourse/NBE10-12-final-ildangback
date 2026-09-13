package com.gommit.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gommit.domain.group.service.GroupService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.security.jwt.JwtProvider;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class StompAuthInterceptorTest {

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final String TOKEN = "valid.access.token";

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private GroupService groupService;

    @Mock
    private MessageChannel channel;

    @InjectMocks
    private StompAuthInterceptor stompAuthInterceptor;

    private Message<byte[]> frame(StompCommand command, Consumer<StompHeaderAccessor> customizer) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        customizer.accept(accessor);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void loginAs(StompHeaderAccessor accessor) {
        SecurityUser user = new SecurityUser(USER_ID, "USER");
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @Nested
    @DisplayName("접속")
    class Connect {

        @Test
        @DisplayName("Authorization 헤더가 없으면 접속할 수 없다")
        void rejectsMissingHeader() {
            Message<byte[]> message = frame(StompCommand.CONNECT, accessor -> {});

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Bearer 가 아닌 인증 방식은 접속할 수 없다")
        void rejectsNonBearerScheme() {
            Message<byte[]> message = frame(
                    StompCommand.CONNECT,
                    accessor -> accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Basic " + TOKEN));

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);

            verify(jwtProvider, never()).parse(any());
        }

        @Test
        @DisplayName("Bearer 뒤가 비어 있으면 접속할 수 없다")
        void rejectsEmptyToken() {
            Message<byte[]> message = frame(
                    StompCommand.CONNECT, accessor -> accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer   "));

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);

            verify(jwtProvider, never()).parse(any());
        }

        @Test
        @DisplayName("토큰을 해석할 수 없으면 접속할 수 없다")
        void rejectsInvalidToken() {
            when(jwtProvider.parse(TOKEN)).thenReturn(null);
            Message<byte[]> message = frame(
                    StompCommand.CONNECT,
                    accessor -> accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }

        @Test
        @DisplayName("유효한 토큰이면 세션에 사용자가 심긴다")
        void setsUserOnValidToken() {
            when(jwtProvider.parse(TOKEN)).thenReturn(new SecurityUser(USER_ID, "USER"));
            Message<byte[]> message = frame(
                    StompCommand.CONNECT,
                    accessor -> accessor.setNativeHeader(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));

            stompAuthInterceptor.preSend(message, channel);

            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            assertThat(accessor.getUser()).isNotNull();
            assertThat(accessor.getUser().getName()).isEqualTo(String.valueOf(USER_ID));
        }
    }

    @Nested
    @DisplayName("구독")
    class Subscribe {

        @Test
        @DisplayName("그룹 토픽이 아닌 주소는 구독할 수 없다")
        void rejectsUnknownDestination() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, accessor -> {
                loginAs(accessor);
                accessor.setDestination("/topic/anything");
            });

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ACCESS_DENIED);

            verify(groupService, never()).isActiveMember(any(), any());
        }

        @Test
        @DisplayName("그룹 식별자가 숫자가 아니면 구독할 수 없다")
        void rejectsNonNumericGroupId() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, accessor -> {
                loginAs(accessor);
                accessor.setDestination("/topic/groups/abc");
            });

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("ACTIVE 멤버가 아니면 그룹 토픽을 구독할 수 없다")
        void rejectsNonMember() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(false);
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, accessor -> {
                loginAs(accessor);
                accessor.setDestination("/topic/groups/" + GROUP_ID);
            });

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.NOT_GROUP_MEMBER);
        }

        @Test
        @DisplayName("ACTIVE 멤버는 그룹 토픽을 구독할 수 있다")
        void allowsActiveMember() {
            when(groupService.isActiveMember(GROUP_ID, USER_ID)).thenReturn(true);
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, accessor -> {
                loginAs(accessor);
                accessor.setDestination("/topic/groups/" + GROUP_ID);
            });

            assertThat(stompAuthInterceptor.preSend(message, channel)).isSameAs(message);
        }

        @Test
        @DisplayName("개인 에러 큐는 멤버십 확인 없이 구독할 수 있다")
        void allowsErrorQueueSubscription() {
            Message<byte[]> message = frame(StompCommand.SUBSCRIBE, accessor -> {
                loginAs(accessor);
                accessor.setDestination("/user/queue/errors");
            });

            assertThat(stompAuthInterceptor.preSend(message, channel)).isSameAs(message);

            verify(groupService, never()).isActiveMember(any(), any());
        }

        @Test
        @DisplayName("접속 인증을 거치지 않은 세션은 구독할 수 없다")
        void rejectsUnauthenticatedSession() {
            Message<byte[]> message =
                    frame(StompCommand.SUBSCRIBE, accessor -> accessor.setDestination("/topic/groups/" + GROUP_ID));

            assertThatThrownBy(() -> stompAuthInterceptor.preSend(message, channel))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.UNAUTHORIZED);
        }
    }
}

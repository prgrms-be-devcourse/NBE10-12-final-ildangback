package com.gommit.global.config;

import com.gommit.global.security.StompAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String TOPIC_PREFIX = "/topic";
    private static final String QUEUE_PREFIX = "/queue";
    private static final String USER_PREFIX = "/user";
    private static final String APP_PREFIX = "/app";

    // 그룹 대화방. 그 그룹 사람들이 함께 구독한다
    public static final String GROUP_TOPIC_PREFIX = TOPIC_PREFIX + "/groups/";

    // 서버가 보낼 때 쓰는 개인 에러 주소
    public static final String ERROR_QUEUE = QUEUE_PREFIX + "/errors";

    // 클라이언트가 구독할 때 쓰는 개인 에러 주소. 스프링이 세션별로 바꿔친다
    public static final String ERROR_QUEUE_SUBSCRIPTION = USER_PREFIX + ERROR_QUEUE;

    private final StompAuthInterceptor stompAuthInterceptor;

    @Value("${cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(TOPIC_PREFIX, QUEUE_PREFIX);
        registry.setUserDestinationPrefix(USER_PREFIX);
        registry.setApplicationDestinationPrefixes(APP_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}

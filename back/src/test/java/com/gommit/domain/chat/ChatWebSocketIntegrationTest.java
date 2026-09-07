package com.gommit.domain.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.chat.dto.request.ChatMessageSendRequest;
import com.gommit.domain.chat.dto.response.ChatMessageResponse;
import com.gommit.global.exception.ErrorResponse;
import com.gommit.support.IntegrationTestSupport;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("그룹 채팅 웹소켓")
class ChatWebSocketIntegrationTest extends IntegrationTestSupport {

    private static final String OWNER_EMAIL = "wsowner@example.com";
    private static final String OWNER_NICKNAME = "방장";
    private static final String STRANGER_EMAIL = "wsstranger@example.com";
    private static final String STRANGER_NICKNAME = "지나가는사람";
    private static final String ERROR_QUEUE = "/user/queue/errors";

    @Value("${local.server.port}")
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDownClient() {
        stompClient.stop();
    }

    private StompSession connect(String accessToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (accessToken != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return stompClient
                .connectAsync(
                        "ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private String groupCreateBody() {
        return """
                {
                  "name": "오운완 모임",
                  "description": "함께 인증하는 그룹",
                  "category": "EXERCISE",
                  "mapType": "GYM",
                  "visibility": "PUBLIC",
                  "maxMembers": 6,
                  "challenge": {
                    "startDate": "%s",
                    "endDate": "%s",
                    "frequencyType": "DAILY",
                    "frequencyValue": null,
                    "daysOfWeek": null,
                    "dailyCheckInCount": 1,
                    "allowedTypes": ["PHOTO"]
                  }
                }
                """.formatted(LocalDate.now().plusDays(1), LocalDate.now().plusDays(7));
    }

    private Long createGroup(String accessToken, String email) throws Exception {
        mockMvc.perform(jsonRequest(withToken(post("/api/groups"), accessToken), groupCreateBody()))
                .andExpect(status().isCreated());
        return jdbcTemplate.queryForObject(
                "select max(g.id) from challenge_groups g join users u on g.owner_id = u.id where u.email = ?",
                Long.class,
                email);
    }

    private <T> BlockingQueue<T> subscribe(StompSession session, String destination, Class<T> type) {
        BlockingQueue<T> received = new ArrayBlockingQueue<>(1);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return type;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add(type.cast(payload));
            }
        });
        return received;
    }

    private void awaitDisconnect(StompSession session) throws Exception {
        for (int i = 0; i < 50 && session.isConnected(); i++) {
            Thread.sleep(100);
        }
    }

    @Test
    @DisplayName("토큰이 없으면 접속할 수 없다")
    void rejectsConnectWithoutToken() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("멤버가 보낸 메시지가 구독자에게 전달되고 저장된다")
    void broadcastsAndPersistsMessage() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
        Long groupId = createGroup(owner.accessToken(), OWNER_EMAIL);

        StompSession session = connect(owner.accessToken());
        BlockingQueue<ChatMessageResponse> received = new ArrayBlockingQueue<>(1);
        session.subscribe("/topic/groups/" + groupId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessageResponse.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((ChatMessageResponse) payload);
            }
        });

        session.send("/app/groups/" + groupId + "/messages", new ChatMessageSendRequest("오늘도 화이팅"));

        ChatMessageResponse message = received.poll(5, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.content()).isEqualTo("오늘도 화이팅");
        assertThat(message.senderNickname()).isEqualTo(OWNER_NICKNAME);

        Integer saved = jdbcTemplate.queryForObject(
                "select count(*) from group_messages where group_id = ?", Integer.class, groupId);
        assertThat(saved).isEqualTo(1);
    }

    @Test
    @DisplayName("멤버가 아닌 그룹의 토픽은 구독할 수 없다")
    void rejectsSubscribeToOtherGroup() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
        Long groupId = createGroup(owner.accessToken(), OWNER_EMAIL);

        var stranger = loginAs(STRANGER_EMAIL, STRANGER_NICKNAME);
        StompSession session = connect(stranger.accessToken());

        session.subscribe("/topic/groups/" + groupId, new StompSessionHandlerAdapter() {});

        awaitDisconnect(session);
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    @DisplayName("멤버가 아닌 그룹에 보내면 개인 에러 큐로 사유가 온다")
    void sendsErrorToPersonalQueueWhenNotMember() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
        Long groupId = createGroup(owner.accessToken(), OWNER_EMAIL);

        var stranger = loginAs(STRANGER_EMAIL, STRANGER_NICKNAME);
        StompSession session = connect(stranger.accessToken());
        BlockingQueue<ErrorResponse> errors = subscribe(session, ERROR_QUEUE, ErrorResponse.class);

        session.send("/app/groups/" + groupId + "/messages", new ChatMessageSendRequest("몰래 보내기"));

        ErrorResponse error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("NOT_GROUP_MEMBER");
    }

    @Test
    @DisplayName("빈 메시지를 보내면 개인 에러 큐로 검증 실패가 온다")
    void sendsErrorToPersonalQueueWhenContentIsBlank() throws Exception {
        var owner = loginAs(OWNER_EMAIL, OWNER_NICKNAME);
        Long groupId = createGroup(owner.accessToken(), OWNER_EMAIL);

        StompSession session = connect(owner.accessToken());
        BlockingQueue<ErrorResponse> errors = subscribe(session, ERROR_QUEUE, ErrorResponse.class);

        session.send("/app/groups/" + groupId + "/messages", new ChatMessageSendRequest("   "));

        ErrorResponse error = errors.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo("INVALID_INPUT_VALUE");
        assertThat(error.errors()).extracting(ErrorResponse.FieldError::field).contains("content");
    }
}

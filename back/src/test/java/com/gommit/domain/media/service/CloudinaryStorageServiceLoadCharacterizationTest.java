package com.gommit.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cloudinary.Cloudinary;
import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

// docs/media-restclient-migration.md 특성화(characterization) 테스트.
// fetchResource()는 HttpClient/RestClient 클라이언트 종류를 안 가리는 와이어레벨 진입점이다.
//
// 주의: 3-arg 생성자(RestClient.Builder 주입용, MediaConfig 가 씀)는 넘겨받은 빌더의 requestFactory 를
// CONNECT_TIMEOUT/REQUEST_TIMEOUT(5s/30s)으로 항상 덮어쓴다 — Boot 오토컨피그 기본 빌더에 타임아웃이
// 없어도 무제한 대기로 새지 않게 하려는 의도적 설계. 그래서 테스트가 "더 짧은" 타임아웃을 검증하려면
// 3-arg 생성자로는 안 되고, 아래 package-private 5-arg 생성자로 직접 넣어야 한다
// (한 번 이 착각으로 타임아웃이 실제로는 30초로 걸려서 슬로우 테스트가 실패한 적 있음 — 재발 방지 메모).
@DisplayName("CloudinaryStorageService.fetchResource (RestClient 마이그레이션 특성화)")
class CloudinaryStorageServiceLoadCharacterizationTest {

    private final CloudinaryStorageService service = new CloudinaryStorageService(
            mock(Cloudinary.class),
            new MediaStorageProperties(null, null, null, Map.of()),
            RestClient.builder(),
            Duration.ofSeconds(5),
            Duration.ofMillis(200));

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("200 이면 응답 바이트를 그대로 담고, 넘긴 파일명으로 Resource 를 돌려준다")
    void ok() throws IOException {
        byte[] body = {1, 2, 3, 4};
        server = startServer(exchange -> {
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        Resource resource = service.fetchResource(urlOf(server), "abc.jpg");

        assertThat(resource.getContentAsByteArray()).isEqualTo(body);
        assertThat(resource.getFilename()).isEqualTo("abc.jpg");
    }

    @Test
    @DisplayName("404 면 MEDIA_NOT_FOUND")
    void notFound() throws IOException {
        server = startServer(exchange -> respondEmpty(exchange, 404));

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg"))
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    @DisplayName("500 은 MEDIA_STORAGE_FAILED — Cloudinary 쪽 실패는 NOT_FOUND 가 아니라 STORAGE_FAILED (의도적 동작 수정)")
    void serverError() throws IOException {
        server = startServer(exchange -> respondEmpty(exchange, 500));

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg"))
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_STORAGE_FAILED));
    }

    @Test
    @DisplayName("200 인데 바디가 없으면 MEDIA_STORAGE_FAILED — RestClient 는 빈 바디에 null 을 주는데(HttpClient 시절엔 항상 "
            + "길이 0 배열이었음) ByteArrayResource(null) 로 새지 않게 막는다")
    void okWithEmptyBody() throws IOException {
        server = startServer(exchange -> respondEmpty(exchange, 200));

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg"))
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_STORAGE_FAILED));
    }

    @Test
    @DisplayName("응답이 요청 타임아웃보다 느리면 MEDIA_STORAGE_FAILED (짧은 타임아웃으로 검증, 30초 실측 대기 없음)")
    void slowResponseTimesOut() throws IOException {
        server = startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respondEmpty(exchange, 200);
        });

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg"))
                .satisfies(e ->
                        assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_STORAGE_FAILED));
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        return server;
    }

    private static String urlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/resource";
    }
}

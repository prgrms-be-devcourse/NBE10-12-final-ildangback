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
// fetchResource()는 HttpClient/RestClient 클라이언트 종류를 안 가리는 와이어레벨 진입점이라,
// 여기 있는 케이스가 HttpClient -> RestClient 교체 전후로 그대로 통과해야 회귀 없다는 증거가 된다.
// (교체 방향은 문서 "보강 5번" 확정: 200 아니면 전부 MEDIA_NOT_FOUND — 시맨틱 변경 없음)
@DisplayName("CloudinaryStorageService.fetchResource (RestClient 마이그레이션 특성화)")
class CloudinaryStorageServiceLoadCharacterizationTest {

    private final CloudinaryStorageService service = new CloudinaryStorageService(
            mock(Cloudinary.class), new MediaStorageProperties(null, null, null, Map.of()));

    // 느린 응답 케이스에서 30초 실측 대기가 나지 않도록, 운영용 5s/30s 대신 짧은 타임아웃의
    // RestClient 를 fetchResource() 오버로드에 직접 넣는다(docs/media-restclient-migration.md 보강 4번).
    // 구성 체인 자체는 CloudinaryStorageService.requestFactory() 를 그대로 재사용 — 생성자와 중복 안 되게.
    private static final RestClient SHORT_TIMEOUT_CLIENT = RestClient.builder()
            .requestFactory(CloudinaryStorageService.requestFactory(Duration.ofSeconds(5), Duration.ofMillis(200)))
            .build();

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

        Resource resource = service.fetchResource(urlOf(server), "abc.jpg", SHORT_TIMEOUT_CLIENT);

        assertThat(resource.getContentAsByteArray()).isEqualTo(body);
        assertThat(resource.getFilename()).isEqualTo("abc.jpg");
    }

    @Test
    @DisplayName("404 면 MEDIA_NOT_FOUND")
    void notFound() throws IOException {
        server = startServer(exchange -> respondEmpty(exchange, 404));

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg", SHORT_TIMEOUT_CLIENT))
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    @DisplayName("500 도 MEDIA_NOT_FOUND — 200 아니면 전부 동일 취급(기존 시맨틱 유지, 회귀 아님)")
    void serverError() throws IOException {
        server = startServer(exchange -> respondEmpty(exchange, 500));

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg", SHORT_TIMEOUT_CLIENT))
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
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

        assertThatThrownBy(() -> service.fetchResource(urlOf(server), "abc.jpg", SHORT_TIMEOUT_CLIENT))
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

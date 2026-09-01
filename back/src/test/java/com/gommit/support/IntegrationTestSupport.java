package com.gommit.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String DEFAULT_PASSWORD = "P@ssw0rd123";

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static List<String> tableNames;

    static {
        MYSQL.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    // JSON 본문을 붙인다.
    protected static MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // Authorization 헤더를 붙인다.
    protected static MockHttpServletRequestBuilder withToken(
            MockHttpServletRequestBuilder builder, String accessToken) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    }

    // json("a", "1", "b", null) → {"a":"1","b":null}
    protected static String json(String... keyValues) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(keyValues[i]).append("\":");
            sb.append(keyValues[i + 1] == null ? "null" : "\"" + keyValues[i + 1] + "\"");
        }
        return sb.append('}').toString();
    }

    // 응답 본문에서 필드 하나를 꺼낸다.
    protected static String fieldOf(String responseBody, String field) {
        return MAPPER.readTree(responseBody).get(field).asString();
    }

    // TRUNCATE 가 아니라 DELETE 다 — 실측 292ms 대 22ms, 전체 23.6초 → 15.2초.
    // 테스트 메서드마다 모든 테이블의 행을 비운다.
    @BeforeEach
    void clearDatabase() {
        if (tableNames == null) {
            tableNames = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables"
                            + " WHERE table_schema = DATABASE()"
                            + " AND table_name <> 'flyway_schema_history'",
                    String.class);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        tableNames.forEach(table -> jdbcTemplate.execute("DELETE FROM " + table));
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    // 사용자를 만들고 로그인해 토큰을 돌려준다. 어느 도메인 테스트든 여기서 시작한다.
    protected Tokens loginAs(String email, String nickname) {
        try {
            mockMvc.perform(jsonRequest(
                    post("/api/auth/signup"),
                    json(
                            "email", email,
                            "password", DEFAULT_PASSWORD,
                            "nickname", nickname)));

            String body = mockMvc.perform(
                            jsonRequest(post("/api/auth/login"), json("email", email, "password", DEFAULT_PASSWORD)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            return new Tokens(fieldOf(body, "accessToken"), fieldOf(body, "refreshToken"));
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 로그인에 실패했다.", e);
        }
    }

    protected Tokens loginAs() {
        return loginAs("tester@example.com", "테스터");
    }

    public record Tokens(String accessToken, String refreshToken) {}
}

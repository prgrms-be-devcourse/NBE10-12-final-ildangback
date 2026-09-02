package com.gommit.domain.item;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

// IntegrationTestSupport를 상속받아 MySQL Testcontainer + MockMvc 환경을 재사용한다.
// @BeforeEach에서 clearDatabase()가 실행되므로 각 테스트는 빈 DB 상태에서 시작한다.
@DisplayName("아이템 API")
class ItemApiIntegrationTest extends IntegrationTestSupport {

    // ─── 공통 헬퍼 ───────────────────────────────────────────────────────────

    // items 테이블에 아이템을 직접 삽입하고 생성된 AUTO_INCREMENT id를 반환한다.
    // 관리자 계정이 없어도 테스트용 데이터를 만들 수 있도록 SQL로 직접 삽입한다.
    private long insertItem(String slot, String name, int price) {
        jdbcTemplate.update(
                "INSERT INTO items (slot, name, image_url, price, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NOW(), NOW())",
                slot,
                name,
                "https://cdn.example.com/test.png",
                price);
        // MySQL에서 직전에 삽입된 행의 id를 가져온다.
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    // 일반 유저로 가입 후 DB에서 role을 ADMIN으로 변경하고 재로그인해 ADMIN 토큰을 반환한다.
    // JWT는 로그인 시점의 role을 담으므로, role 변경 후 반드시 재로그인이 필요하다.
    private Tokens loginAsAdmin(String email, String nickname) throws Exception {
        loginAs(email, nickname); // 회원가입 + USER 토큰 발급 (토큰은 버린다)
        // users 테이블의 role 컬럼을 ADMIN으로 직접 변경
        // SecurityUser.getAuthorities()에서 "ROLE_" + role 로 조합되므로 ADMIN이면 ROLE_ADMIN이 된다.
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        // 재로그인해서 ADMIN role이 담긴 새 JWT를 발급받는다.
        String body = mockMvc.perform(jsonRequest(
                        post("/api/auth/login"),
                        json("email", email, "password", DEFAULT_PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        return new Tokens(fieldOf(body, "accessToken"), fieldOf(body, "refreshToken"));
    }

    // ─── 상점 아이템 조회 ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("상점 아이템 조회 GET /api/items")
    class GetShopItems {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            // Authorization 헤더 없이 요청하면 JwtFilter가 인증 정보를 찾지 못해
            // CustomAuthenticationEntryPoint가 401을 응답한다.
            mockMvc.perform(get("/api/items"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("인증하면 200 과 SliceResponse 구조를 돌려준다")
        void 인증_200() throws Exception {
            // loginAs()는 회원가입 + 로그인을 대행하고 accessToken을 포함한 Tokens를 반환한다.
            var tokens = loginAs();
            // 상점에 아이템이 있어야 content 배열이 비어있지 않아 구조 검증에 유리하다.
            insertItem("HEAD", "기본 모자", 100);

            mockMvc.perform(withToken(get("/api/items"), tokens.accessToken()))
                    .andExpect(status().isOk())
                    // SliceResponse의 content 필드가 JSON 배열로 존재하는지 확인
                    .andExpect(jsonPath("$.content").isArray())
                    // SliceResponse의 hasNext 필드가 boolean 타입으로 존재하는지 확인
                    .andExpect(jsonPath("$.hasNext").isBoolean());
        }

        @Test
        @DisplayName("size=0 이면 400 — @Min(1) 제약")
        void size_0이면_400() throws Exception {
            var tokens = loginAs();

            // 컨트롤러 파라미터에 @Min(1)이 붙어있으므로 size=0은 Bean Validation에서 400으로 거절된다.
            // 이 검증은 단위 테스트로는 확인할 수 없고 통합 테스트에서만 실제 동작이 보장된다.
            mockMvc.perform(withToken(get("/api/items").param("size", "0"), tokens.accessToken()))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─── 아이템 구매 ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("아이템 구매 POST /api/items/{itemId}/purchase")
    class PurchaseItem {

        @Test
        @DisplayName("미인증이면 401")
        void 미인증_401() throws Exception {
            // 구매도 인증이 필요한 엔드포인트이므로 토큰 없이 요청하면 401이다.
            mockMvc.perform(post("/api/items/1/purchase")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하는 아이템 구매 시 201 과 응답 필드를 확인한다")
        void 구매성공_201() throws Exception {
            var tokens = loginAs();
            // 구매할 아이템을 DB에 먼저 준비한다.
            long itemId = insertItem("HEAD", "기본 모자", 100);

            mockMvc.perform(withToken(post("/api/items/" + itemId + "/purchase"), tokens.accessToken()))
                    .andExpect(status().isCreated()) // 구매 성공은 201 Created
                    .andExpect(jsonPath("$.userItemId").exists()) // 생성된 UserItem의 id
                    .andExpect(jsonPath("$.itemId").value(itemId)); // 구매한 Item의 id
        }

        @Test
        @DisplayName("존재하지 않는 아이템 구매 시 404")
        void 없는아이템_404() throws Exception {
            var tokens = loginAs();

            // DB에 없는 id로 구매 시도 → ItemService에서 ITEM_NOT_FOUND 예외 발생 → 404
            mockMvc.perform(withToken(post("/api/items/999999/purchase"), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        }

        @Test
        @DisplayName("이미 보유한 아이템 재구매 시 409")
        void 중복구매_409() throws Exception {
            var tokens = loginAs();
            long itemId = insertItem("HEAD", "기본 모자", 100);

            // 첫 번째 구매 (성공)
            mockMvc.perform(withToken(post("/api/items/" + itemId + "/purchase"), tokens.accessToken()));

            // 동일 아이템 재구매 시도 → existsByUserIdAndItemId가 true → ALREADY_OWNED_ITEM → 409
            mockMvc.perform(withToken(post("/api/items/" + itemId + "/purchase"), tokens.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_OWNED_ITEM"));
        }
    }

    // ─── 관리자 API 권한 검사 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("관리자 아이템 등록/삭제 — 권한 검사")
    class AdminEndpoints {

        @Test
        @DisplayName("미인증으로 아이템 등록하면 401")
        void 미인증_아이템등록_401() throws Exception {
            // 토큰 없이 관리자 엔드포인트 접근 → SecurityConfig의 anyRequest().authenticated()에서 401
            mockMvc.perform(multipart("/api/admin/items")
                            .file(new MockMultipartFile("image", "hat.png", "image/png", new byte[] {1}))
                            .param("slot", "HEAD")
                            .param("name", "테스트 모자")
                            .param("price", "100"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("일반 유저가 아이템 등록하면 403")
        void 일반유저_아이템등록_403() throws Exception {
            // loginAs()로 만든 유저는 ROLE_USER이므로
            // SecurityConfig의 .hasRole("ADMIN") 조건에 걸려 403이 반환된다.
            // 이 테스트가 SecurityConfig의 "/api/admin/**" 패턴이 올바르게 동작하는지 보장한다.
            var tokens = loginAs();

            // withToken()이 MockHttpServletRequestBuilder만 받으므로
            // 멀티파트 요청은 헤더를 체인으로 직접 추가한다.
            mockMvc.perform(multipart("/api/admin/items")
                            .file(new MockMultipartFile("image", "hat.png", "image/png", new byte[] {1}))
                            .param("slot", "HEAD")
                            .param("name", "테스트 모자")
                            .param("price", "100")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("미인증으로 아이템 삭제하면 401")
        void 미인증_아이템삭제_401() throws Exception {
            mockMvc.perform(delete("/api/admin/items/1")).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("일반 유저가 아이템 삭제하면 403")
        void 일반유저_아이템삭제_403() throws Exception {
            var tokens = loginAs();

            mockMvc.perform(withToken(delete("/api/admin/items/1"), tokens.accessToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("관리자가 아이템 등록하면 201 과 생성된 아이템 정보를 돌려준다")
        void 관리자_아이템등록_201() throws Exception {
            // 일반 유저로 가입 후 DB에서 ADMIN으로 변경해 재로그인한다.
            var tokens = loginAsAdmin("admin@example.com", "관리자");

            // withToken()이 MockMultipartHttpServletRequestBuilder를 받지 못하므로 헤더를 직접 추가한다.
            mockMvc.perform(multipart("/api/admin/items")
                            .file(new MockMultipartFile(
                                    "image", "hat.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1}))
                            .param("slot", "HEAD")
                            .param("name", "관리자 모자")
                            .param("price", "500")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.accessToken()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())       // DB가 부여한 id가 응답에 있어야 한다
                    .andExpect(jsonPath("$.name").value("관리자 모자"))
                    .andExpect(jsonPath("$.slot").value("HEAD"));
        }

        @Test
        @DisplayName("관리자가 보유자 없는 아이템 삭제하면 204")
        void 관리자_아이템삭제_204() throws Exception {
            // 삭제 대상 아이템을 DB에 직접 준비한다.
            long itemId = insertItem("TOP", "삭제용 상의", 0);
            var tokens = loginAsAdmin("admin2@example.com", "관리자2");

            mockMvc.perform(withToken(delete("/api/admin/items/" + itemId), tokens.accessToken()))
                    .andExpect(status().isNoContent()); // 204 No Content
        }

        @Test
        @DisplayName("관리자가 보유자 있는 아이템 삭제 시도하면 409")
        void 관리자_사용중인아이템삭제_409() throws Exception {
            long itemId = insertItem("TOP", "삭제용 상의", 0);

            // 일반 유저가 해당 아이템을 구매해 보유 상태로 만든다.
            var userTokens = loginAs("buyer@example.com", "구매자");
            mockMvc.perform(withToken(post("/api/items/" + itemId + "/purchase"), userTokens.accessToken()));

            // 관리자가 삭제 시도 → existsByItemId = true → ITEM_IN_USE → 409
            var adminTokens = loginAsAdmin("admin3@example.com", "관리자3");
            mockMvc.perform(withToken(delete("/api/admin/items/" + itemId), adminTokens.accessToken()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ITEM_IN_USE"));
        }
    }
}

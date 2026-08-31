package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gommit.domain.user.dto.request.SignUpRequest;
import com.gommit.domain.user.service.AuthService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

@DisplayName("사용자 API")
class UserApiIntegrationTest extends IntegrationTestSupport {

    private static final String EMAIL = "gommit@example.com";
    private static final String NICKNAME = "꼬밋러";

    @Autowired
    private AuthService authService;

    private ResultActions signUp(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(jsonRequest(
                post("/api/auth/signup"), json("email", email, "password", password, "nickname", nickname)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(jsonRequest(post("/api/auth/login"), json("email", email, "password", password)));
    }

    private ResultActions getMe(String accessToken) throws Exception {
        return mockMvc.perform(withToken(get("/api/users/me"), accessToken));
    }

    // rawJson 을 그대로 보낸다 — 키 생략과 명시적 null 을 구분해 검증해야 한다
    private ResultActions patchMe(String accessToken, String rawJson) throws Exception {
        return mockMvc.perform(jsonRequest(withToken(patch("/api/users/me"), accessToken), rawJson));
    }

    // ===================================================================
    // user 도메인 엔드포인트 호출. 공용 기반이 아니라 여기 둔다.
    // ===================================================================

    private ResultActions changePassword(String accessToken, String current, String next) throws Exception {
        return mockMvc.perform(jsonRequest(
                withToken(patch("/api/users/me/password"), accessToken),
                json("currentPassword", current, "newPassword", next)));
    }

    private ResultActions deleteAccount(String accessToken, String password) throws Exception {
        return mockMvc.perform(
                jsonRequest(withToken(delete("/api/users/me"), accessToken), json("password", password)));
    }

    // 탈퇴 후 이메일이 재사용 가능해졌는지 확인하는 데 쓴다
    private ResultActions checkEmail(String email) throws Exception {
        return mockMvc.perform(get("/api/auth/check-email").param("email", email));
    }

    @Nested
    @DisplayName("회원가입")
    class SignUp {

        // signUp 의 try/catch (DataIntegrityViolationException) 가 존재하는 이유가 여기서만 확인된다.
        // 사전 검사 existsByEmail 은 동시 요청을 막지 못한다 — 전부 통과한 뒤 UK 가 최종 방어한다.
        // MockMvc 는 스레드 안전을 보장하지 않아 서비스를 직접 호출한다.
        @Test
        @DisplayName("같은 이메일로 동시에 8건이 와도 한 건만 가입된다")
        void concurrentSignUpsCreateOnlyOne() throws Exception {
            int parallel = 8;
            CyclicBarrier barrier = new CyclicBarrier(parallel);
            List<Callable<Boolean>> tasks = new ArrayList<>();

            for (int i = 0; i < parallel; i++) {
                String nickname = "꼬밋러" + i;
                tasks.add(() -> {
                    barrier.await();
                    try {
                        authService.signUp(new SignUpRequest(EMAIL, DEFAULT_PASSWORD, nickname));
                        return true;
                    } catch (BusinessException e) {
                        assertThat(e.getErrorCode())
                                .as("중복은 사전 검사나 UK 위반 둘 중 하나로만 걸러진다")
                                .isIn(ErrorCode.EMAIL_DUPLICATED, ErrorCode.ACCOUNT_INFO_DUPLICATED);
                        return false;
                    }
                });
            }

            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            long succeeded;
            try {
                List<Future<Boolean>> results = pool.invokeAll(tasks);
                succeeded = 0;
                for (Future<Boolean> result : results) {
                    if (result.get()) {
                        succeeded++;
                    }
                }
            } finally {
                pool.shutdownNow();
            }

            assertThat(succeeded).as("동시 요청이어도 가입은 한 건만 성공해야 한다").isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("select count(*) from users where email = ?", Integer.class, EMAIL))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("가입하면 201 과 요약 정보를 주고 토큰은 주지 않는다")
        void returns201WithSummaryAndNoToken() throws Exception {
            signUp(EMAIL, DEFAULT_PASSWORD, NICKNAME)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    .andExpect(jsonPath("$.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.accessToken").doesNotExist());
        }

        @Test
        @DisplayName("검증에 실패하면 400 과 위반 필드 목록을 준다")
        void returns400WithFieldErrors() throws Exception {
            signUp("not-an-email", "short", NICKNAME)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        }

        @Test
        @DisplayName("9자 비밀번호는 400 — 하한은 10자다")
        void rejectsShortPassword() throws Exception {
            signUp(EMAIL, "P@ssw0rd1", NICKNAME) // 9자
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        }

        // ASCII 로 좁혀 둔 덕분에 글자 수 = 바이트 수라 @Size 만으로 BCrypt 상한을 막는다
        @Test
        @DisplayName("72자를 넘는 비밀번호는 400 — BCrypt 가 72바이트까지만 받는다")
        void rejectsPasswordOverBcryptLimit() throws Exception {
            String password = "Aa1" + "b".repeat(70); // 73자 / 73바이트

            signUp(EMAIL, password, NICKNAME)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        }

        // 한글을 허용하면 25자만 넘어도 72바이트를 넘겨 BCrypt 에서 500 이 난다. 그래서 문자 집합부터 막는다.
        @Test
        @DisplayName("한글이 섞인 비밀번호는 400")
        void rejectsNonAsciiPassword() throws Exception {
            signUp(EMAIL, "비밀번호abc123", NICKNAME)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
        }

        // 특수문자는 허용하되 강제하지 않는다. 나중에 정규식을 조이면 이 테스트가 막는다.
        @Test
        @DisplayName("특수문자가 들어간 비밀번호도 201")
        void acceptsSpecialCharacters() throws Exception {
            signUp(EMAIL, "P@$$w0rd!#%", NICKNAME).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("이모지 닉네임은 400")
        void rejectsEmojiNickname() throws Exception {
            signUp(EMAIL, DEFAULT_PASSWORD, "꼬밋러🔥")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("nickname")));
        }

        @Test
        @DisplayName("이메일이 겹치면 409")
        void rejectsDuplicateEmail() throws Exception {
            loginAs(EMAIL, NICKNAME);

            signUp(EMAIL, DEFAULT_PASSWORD, "다른닉")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        }

        @Test
        @DisplayName("닉네임이 겹치면 409")
        void rejectsDuplicateNickname() throws Exception {
            loginAs(EMAIL, NICKNAME);

            signUp("other@example.com", DEFAULT_PASSWORD, NICKNAME)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        }

        // 운영(MySQL)과 dev(H2)가 갈리는 지점이다. MySQL 의 utf8mb4_0900_ai_ci 는 대소문자를 같게 보므로 사전
        // 검사가 걸러 409 가 된다. H2 는 다르게 보아 둘 다 가입된다.
        @Test
        @DisplayName("대소문자만 다른 이메일은 중복으로 걸린다")
        void treatsEmailCaseInsensitively() throws Exception {
            loginAs(EMAIL, NICKNAME);

            signUp("GOMMIT@Example.com", DEFAULT_PASSWORD, "다른닉")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
        }
    }

    @Nested
    @DisplayName("내 정보")
    class Profile {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("토큰 없이 수정하면 401")
        void updateRequiresAuthentication() throws Exception {
            mockMvc.perform(jsonRequest(patch("/api/users/me"), json("nickname", "새닉네임")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("로그인하면 프로필과 스트릭을 준다")
        void returnsProfile() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            getMe(tokens.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(EMAIL))
                    .andExpect(jsonPath("$.nickname").value(NICKNAME))
                    .andExpect(jsonPath("$.personalStreak").value(0));
        }

        // 이 셋이 이 도메인에서 가장 깨지기 쉬운 규약이다 — 생략(null)과 비움("")이 갈려야 한다
        @Test
        @DisplayName("닉네임만 보내면 한줄소개는 보존된다")
        void omittedFieldIsPreserved() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            patchMe(tokens.accessToken(), "{\"introduction\":\"러닝 중\"}");

            patchMe(tokens.accessToken(), "{\"nickname\":\"새닉네임\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.introduction").value("러닝 중"));
        }

        @Test
        @DisplayName("한줄소개에 빈 문자열을 보내면 값을 비운다")
        void blankIntroductionIsCleared() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);
            patchMe(tokens.accessToken(), "{\"introduction\":\"러닝 중\"}");

            patchMe(tokens.accessToken(), "{\"introduction\":\"\"}")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.introduction").doesNotExist());
        }

        @Test
        @DisplayName("전 필드를 생략하면 400 — 무변경 200 은 반영된 줄 착각하게 한다")
        void rejectsEmptyRequest() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            patchMe(tokens.accessToken(), "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
        }

        @Test
        @DisplayName("현재 본인 닉네임을 그대로 보내도 중복이 아니다")
        void ownNicknameIsNotDuplicate() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            patchMe(tokens.accessToken(), json("nickname", NICKNAME)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("남이 쓰는 닉네임이면 409")
        void othersNicknameIsDuplicate() throws Exception {
            signUp("other@example.com", DEFAULT_PASSWORD, "남의닉");
            var tokens = loginAs(EMAIL, NICKNAME);

            patchMe(tokens.accessToken(), json("nickname", "남의닉"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("NICKNAME_DUPLICATED"));
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePassword {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(jsonRequest(
                            patch("/api/users/me/password"),
                            json("currentPassword", DEFAULT_PASSWORD, "newPassword", "N3wP@ssw0rd")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("변경하면 204 이고 옛 비밀번호로는 로그인할 수 없다")
        void changesPassword() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            changePassword(tokens.accessToken(), DEFAULT_PASSWORD, "N3wP@ssw0rd")
                    .andExpect(status().isNoContent());

            login(EMAIL, "N3wP@ssw0rd").andExpect(status().isOk());
            login(EMAIL, DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 401")
        void rejectsWrongCurrentPassword() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            changePassword(tokens.accessToken(), "wrongpass1", "N3wP@ssw0rd")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class DeleteAccount {

        @Test
        @DisplayName("미인증이면 401")
        void requiresAuthentication() throws Exception {
            mockMvc.perform(jsonRequest(delete("/api/users/me"), json("password", DEFAULT_PASSWORD)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        @DisplayName("탈퇴 후에는 로그인할 수 없고 같은 이메일로 재가입할 수 있다")
        void allowsRejoinWithSameEmail() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            deleteAccount(tokens.accessToken(), DEFAULT_PASSWORD).andExpect(status().isNoContent());

            login(EMAIL, DEFAULT_PASSWORD).andExpect(status().isUnauthorized());
            checkEmail(EMAIL).andExpect(jsonPath("$.available").value(true));
            signUp(EMAIL, DEFAULT_PASSWORD, NICKNAME).andExpect(status().isCreated());
        }

        // 치환 포맷이 deleted_{id_{원본}} 이면 45자 닉네임에서 55자가 되어 MySQL 이 ERROR 1406 을 던진다. H2 는 관대해서
        // 이 회귀를 못 잡는다.
        @Test
        @DisplayName("최대 길이 닉네임도 탈퇴가 성공한다 — 원본을 붙이면 ERROR 1406")
        void succeedsWithMaxLengthNickname() throws Exception {
            String longNickname = "가".repeat(20);
            var tokens = loginAs(EMAIL, longNickname);

            deleteAccount(tokens.accessToken(), DEFAULT_PASSWORD).andExpect(status().isNoContent());

            signUp(EMAIL, DEFAULT_PASSWORD, longNickname).andExpect(status().isCreated());
        }

        @Test
        @DisplayName("탈퇴 치환값은 사용자가 선점할 수 없다 — 밑줄이 닉네임 규칙에 막힌다")
        void replacementNicknameCannotBeClaimed() throws Exception {
            signUp(EMAIL, DEFAULT_PASSWORD, "탈퇴한사용자_1")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[*].field").value(hasItem("nickname")));
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401")
        void rejectsWrongPassword() throws Exception {
            var tokens = loginAs(EMAIL, NICKNAME);

            deleteAccount(tokens.accessToken(), "wrongpass1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }
}

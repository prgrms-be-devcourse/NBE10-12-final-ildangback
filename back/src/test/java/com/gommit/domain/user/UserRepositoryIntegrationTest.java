package com.gommit.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("users 테이블 제약")
class UserRepositoryIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    // ddl-auto validate 는 nullability 를 안 본다. 매핑이 어긋나면 기동은 되고 여기서 터진다
    @Test
    @DisplayName("비밀번호 없는 소셜 전용 사용자가 저장된다")
    void savesUserWithoutPassword() {
        User saved = userRepository.saveAndFlush(new User("social@example.com", "소셜러"));

        assertThat(saved.getPassword()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT password FROM users WHERE id = ?", String.class, saved.getId()))
                .isNull();
    }
}

package com.gommit.domain.report.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Penalty — 로그인 차단 판정")
class PenaltyTest {

    private static final Long REPORT_ID = 1L;
    private static final Long USER_ID = 2L;

    @Test
    @DisplayName("영구 정지는 로그인을 막는다")
    void permanentBanBlocksLogin() {
        Penalty penalty = Penalty.permanentBan(REPORT_ID, USER_ID);

        assertThat(penalty.blocksLogin(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("종료 시각이 남은 기간 정지는 로그인을 막는다")
    void activeSuspensionBlocksLogin() {
        Penalty penalty = Penalty.suspension(REPORT_ID, USER_ID, 3);

        assertThat(penalty.blocksLogin(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("종료 시각이 지난 기간 정지는 로그인을 막지 않는다")
    void expiredSuspensionDoesNotBlockLogin() {
        Penalty penalty = Penalty.suspension(REPORT_ID, USER_ID, 3);

        assertThat(penalty.blocksLogin(LocalDateTime.now().plusDays(4))).isFalse();
    }

    @Test
    @DisplayName("경고는 로그인을 막지 않는다")
    void warningDoesNotBlockLogin() {
        Penalty penalty = Penalty.warning(REPORT_ID, USER_ID);

        assertThat(penalty.blocksLogin(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("포인트 압수는 로그인을 막지 않는다")
    void pointForfeitDoesNotBlockLogin() {
        Penalty penalty = Penalty.pointForfeit(REPORT_ID, USER_ID, 100);

        assertThat(penalty.blocksLogin(LocalDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("해제된 제재는 종류와 상관없이 로그인을 막지 않는다")
    void revokedPenaltyDoesNotBlockLogin() {
        Penalty penalty = Penalty.permanentBan(REPORT_ID, USER_ID);

        penalty.revoke();

        assertThat(penalty.isRevoked()).isTrue();
        assertThat(penalty.blocksLogin(LocalDateTime.now())).isFalse();
    }
}

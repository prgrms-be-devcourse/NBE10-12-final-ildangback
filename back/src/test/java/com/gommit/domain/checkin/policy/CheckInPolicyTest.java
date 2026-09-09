package com.gommit.domain.checkin.policy;

import static com.gommit.domain.checkin.CheckInFixture.START;
import static com.gommit.domain.checkin.CheckInFixture.challenge;
import static com.gommit.domain.checkin.CheckInFixture.dailyChallenge;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.point.config.PointProperties;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInPolicy — 허용 방식 / 인증일 검증(위임)")
class CheckInPolicyTest {

    @Mock
    private ChallengeProgressCalculator progressCalculator;

    private CheckInPolicy policy;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        policy = new CheckInPolicy(progressCalculator, new PointProperties(10, 5, 0, 0));
    }

    @Nested
    @DisplayName("allowedTypes")
    class Allowed {

        @Test
        @DisplayName("allowPhoto 면 PHOTO 만 허용")
        void photoAllowed() {
            assertThat(policy.allowedTypes(dailyChallenge(1L, 1))).containsExactly(CheckInType.PHOTO);
        }

        @Test
        @DisplayName("allowPhoto 가 false 면 아무 방식도 허용하지 않는다")
        void nothingAllowed() {
            Challenge challenge = challenge(1L, FrequencyType.DAILY, null, null, 1, false);
            assertThat(policy.allowedTypes(challenge)).isEqualTo(List.of());
        }
    }

    @Nested
    @DisplayName("validateCheckInDay — ChallengeProgressCalculator.isCheckInDay 에 위임")
    class ValidateCheckInDay {

        @Test
        @DisplayName("대상일이면 통과")
        void passesOnCheckInDay() {
            Challenge challenge = dailyChallenge(1L, 1);
            when(progressCalculator.isCheckInDay(challenge, START)).thenReturn(true);

            policy.validateCheckInDay(challenge, START); // 예외 없음
        }

        @Test
        @DisplayName("대상일이 아니면 NOT_CHECK_IN_DAY")
        void throwsWhenNotCheckInDay() {
            Challenge challenge = dailyChallenge(1L, 1);
            when(progressCalculator.isCheckInDay(challenge, START)).thenReturn(false);

            assertThatThrownBy(() -> policy.validateCheckInDay(challenge, START))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_CHECK_IN_DAY);
        }
    }

    @Nested
    @DisplayName("validateAllowedType")
    class ValidateAllowedType {

        @Test
        @DisplayName("허용된 방식이면 통과, 아니면 CHECK_IN_TYPE_NOT_ALLOWED")
        void validateAllowedType() {
            Challenge photoAllowed = dailyChallenge(1L, 1);
            Challenge nothingAllowed = challenge(1L, FrequencyType.DAILY, null, null, 1, false);

            policy.validateAllowedType(photoAllowed, CheckInType.PHOTO); // 예외 없음

            assertThatThrownBy(() -> policy.validateAllowedType(nothingAllowed, CheckInType.PHOTO))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.CHECK_IN_TYPE_NOT_ALLOWED);
        }
    }
}

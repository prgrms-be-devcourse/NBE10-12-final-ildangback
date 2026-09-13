package com.gommit.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.gommit.domain.challenge.service.ChallengeMemberService;
import com.gommit.domain.group.service.GroupService;
import com.gommit.domain.user.UserFixture;
import com.gommit.domain.user.dto.request.ChangePasswordRequest;
import com.gommit.domain.user.dto.request.DeleteAccountRequest;
import com.gommit.domain.user.dto.request.UpdateProfileRequest;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.entity.EmailTokenType;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.AuthIdentityRepository;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final Long USER_ID = 42L;
    private static final LocalDate D10 = LocalDate.of(2026, 9, 10);

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthIdentityRepository authIdentityRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailTokenService emailTokenService;

    @Mock
    private GroupService groupService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ChallengeMemberService challengeMemberService;

    @Mock
    private BusinessClock businessClock;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = UserFixture.user(USER_ID, "gommit@example.com", "꼬밋러");
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 본인의 모든 RT 를 폐기한다")
    void changePasswordRevokesAllTokens() {
        givenActiveUser();
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
        given(passwordEncoder.encode(anyString())).willReturn("new-encoded");

        userService.changePassword(USER_ID, new ChangePasswordRequest(UserFixture.RAW_PASSWORD, "N3wP@ssw0rd"));

        assertThat(user.getPassword()).isEqualTo("new-encoded");
        verify(refreshTokenService).revokeAll(USER_ID);
        verify(emailTokenService).revokeUnused(USER_ID, EmailTokenType.PASSWORD_RESET);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 401 이고 RT 는 그대로 둔다")
    void changePasswordRejectsWrongCurrentPassword() {
        givenActiveUser();
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

        assertBusinessException(
                () -> userService.changePassword(USER_ID, new ChangePasswordRequest("wrongpass1", "N3wP@ssw0rd")),
                ErrorCode.INVALID_CREDENTIALS);
        verify(refreshTokenService, org.mockito.Mockito.never()).revokeAll(anyLong());
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 같으면 400 이고 RT 는 그대로 둔다")
    void changePasswordRejectsUnchangedPassword() {
        givenActiveUser();
        given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

        assertBusinessException(
                () -> userService.changePassword(
                        USER_ID, new ChangePasswordRequest(UserFixture.RAW_PASSWORD, UserFixture.RAW_PASSWORD)),
                ErrorCode.PASSWORD_UNCHANGED);
        verify(refreshTokenService, org.mockito.Mockito.never()).revokeAll(anyLong());
    }

    @Test
    @DisplayName("이미 쓰는 이메일이면 사용 불가로 답한다")
    void usedEmailIsReportedUnavailable() {
        given(userRepository.existsByEmail("gommit@example.com")).willReturn(false);

        assertThat(userService.isEmailAvailable("gommit@example.com")).isTrue();
    }

    @Test
    @DisplayName("없는 사용자를 조회하면 404")
    void missingUserThrowsNotFound() {
        given(userRepository.findByIdAndDeletedAtIsNull(anyLong())).willReturn(Optional.empty());

        assertBusinessException(() -> userService.getMyProfile(USER_ID), ErrorCode.USER_NOT_FOUND);
    }

    private void givenActiveUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(user));
    }

    private void givenStreak(int personalStreak, LocalDate lastCheckedInDate) {
        ReflectionTestUtils.setField(user, "personalStreak", personalStreak);
        ReflectionTestUtils.setField(user, "bestStreak", personalStreak);
        ReflectionTestUtils.setField(user, "lastCheckedInDate", lastCheckedInDate);
    }

    // null 은 그 사이 인증 의무가 없었다는 뜻이다.
    private void givenRequiredDay(LocalDate requiredDay) {
        given(challengeMemberService.findLastRequiredCheckInDay(USER_ID, D10)).willReturn(requiredDay);
    }

    private void givenTodayIs(LocalDate today) {
        given(businessClock.today()).willReturn(today);
    }

    private void assertBusinessException(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    @Nested
    @DisplayName("프로필 부분 수정")
    class UpdateProfile {

        @Test
        @DisplayName("닉네임만 보내면 한줄소개는 보존된다 — 생략은 null 로 도착한다")
        void omittedFieldIsPreserved() {
            user.updateIntroduction("매일 아침 30분 러닝 중");
            givenActiveUser();
            given(userRepository.existsByNicknameAndIdNot(anyString(), anyLong()))
                    .willReturn(false);

            var response = userService.updateMyProfile(USER_ID, new UpdateProfileRequest("새닉네임", null));

            assertThat(response.nickname()).isEqualTo("새닉네임");
            assertThat(response.introduction()).isEqualTo("매일 아침 30분 러닝 중");
        }

        @Test
        @DisplayName("한줄소개에 빈 문자열을 보내면 값을 비운다")
        void blankIntroductionIsCleared() {
            user.updateIntroduction("매일 아침 30분 러닝 중");
            givenActiveUser();

            var response = userService.updateMyProfile(USER_ID, new UpdateProfileRequest(null, ""));

            assertThat(response.introduction()).isNull();
        }

        @Test
        @DisplayName("현재 본인 닉네임을 그대로 보내도 중복이 아니다")
        void ownNicknameIsNotDuplicate() {
            givenActiveUser();
            given(userRepository.existsByNicknameAndIdNot("꼬밋러", USER_ID)).willReturn(false);

            var response = userService.updateMyProfile(USER_ID, new UpdateProfileRequest("꼬밋러", null));

            assertThat(response.nickname()).isEqualTo("꼬밋러");
        }

        @Test
        @DisplayName("남이 쓰는 닉네임이면 409")
        void othersNicknameIsDuplicate() {
            givenActiveUser();
            given(userRepository.existsByNicknameAndIdNot(anyString(), anyLong()))
                    .willReturn(true);

            assertBusinessException(
                    () -> userService.updateMyProfile(USER_ID, new UpdateProfileRequest("남닉", null)),
                    ErrorCode.NICKNAME_DUPLICATED);
        }

        @Test
        @DisplayName("전 필드를 생략하면 400 — 무변경 200 은 반영된 줄 착각하게 한다")
        void emptyRequestIsRejected() {
            assertBusinessException(
                    () -> userService.updateMyProfile(USER_ID, new UpdateProfileRequest(null, null)),
                    ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Nested
    @DisplayName("회원 탈퇴")
    class DeleteAccount {

        @Test
        @DisplayName("식별자를 치환하고 소셜 연결과 모든 RT 를 폐기한다")
        void deleteAccountReplacesIdentifiersAndRevokesTokens() {
            givenActiveUser();
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            userService.deleteAccount(USER_ID, new DeleteAccountRequest(UserFixture.RAW_PASSWORD));

            assertThat(user.getEmail()).isEqualTo("deleted_42@example.com");
            assertThat(user.getNickname()).isEqualTo("탈퇴한사용자_42");
            assertThat(user.getDeletedAt()).isNotNull();
            verify(authIdentityRepository).deleteByUserId(USER_ID);
            verify(refreshTokenService).revokeAll(USER_ID);
        }

        // 개인정보보호법 제21조. 식별자만 치우고 나머지를 남기면 파기가 덜 된 것이다.
        @Test
        @DisplayName("비밀번호 해시와 한줄소개도 파기한다")
        void deleteAccountErasesPasswordAndIntroduction() {
            givenActiveUser();
            user.updateIntroduction("매일 아침 30분 러닝 중");
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            userService.deleteAccount(USER_ID, new DeleteAccountRequest(UserFixture.RAW_PASSWORD));

            assertThat(user.getIntroduction()).isNull();
            assertThat(user.getPassword()).isEqualTo("(deleted)");
        }

        @Test
        @DisplayName("최대 길이 닉네임도 치환 후 컬럼 상한을 넘지 않는다 — 원본을 붙이면 ERROR 1406")
        void deleteAccountFitsColumnWithMaxLengthNickname() {
            user = UserFixture.user(USER_ID, "gommit@example.com", "가".repeat(20));
            givenActiveUser();
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            userService.deleteAccount(USER_ID, new DeleteAccountRequest(UserFixture.RAW_PASSWORD));

            assertThat(user.getNickname()).hasSizeLessThanOrEqualTo(50);
        }

        @Test
        @DisplayName("그룹 멤버십을 정리한다")
        void deleteAccountLeavesAllGroups() {
            givenActiveUser();
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            userService.deleteAccount(USER_ID, new DeleteAccountRequest(UserFixture.RAW_PASSWORD));

            verify(groupService).leaveAllGroupsOnAccountDeletion(USER_ID);
        }

        // 뒤로 가면 치환된 닉네임이 방장으로 앉는다. 순서를 되돌려도 위 테스트는 초록불이라 여기서만 막힌다.
        @Test
        @DisplayName("그룹 정리는 식별자 치환보다 먼저 돈다")
        void deleteAccountLeavesGroupsBeforeErasingIdentifiers() {
            givenActiveUser();
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);
            willAnswer(invocation -> {
                        assertThat(user.getNickname()).isEqualTo("꼬밋러");
                        assertThat(user.getDeletedAt()).isNull();
                        return null;
                    })
                    .given(groupService)
                    .leaveAllGroupsOnAccountDeletion(USER_ID);

            userService.deleteAccount(USER_ID, new DeleteAccountRequest(UserFixture.RAW_PASSWORD));

            verify(groupService).leaveAllGroupsOnAccountDeletion(USER_ID);
        }

        @Test
        @DisplayName("비밀번호가 틀리면 401")
        void deleteAccountRejectsWrongPassword() {
            givenActiveUser();
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(false);

            assertBusinessException(
                    () -> userService.deleteAccount(USER_ID, new DeleteAccountRequest("wrongpass1")),
                    ErrorCode.INVALID_CREDENTIALS);
        }
    }

    @Nested
    @DisplayName("전역 인증 스트릭 — 갱신(대상일 기준)")
    class UpdateStreak {

        @Test
        @DisplayName("완료 이력이 없으면 스트릭 1")
        void firstCompletion() {
            givenActiveUser();
            givenRequiredDay(D10.minusDays(1));

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(1);
            assertThat(user.getBestStreak()).isEqualTo(1);
            assertThat(user.getLastCheckedInDate()).isEqualTo(D10);
        }

        @Test
        @DisplayName("직전 의무일에 완료했으면 +1")
        void consecutiveWhenLastCompletionCoversRequiredDay() {
            givenActiveUser();
            givenStreak(3, D10.minusDays(1));
            givenRequiredDay(D10.minusDays(1));

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(4);
            assertThat(user.getLastCheckedInDate()).isEqualTo(D10);
        }

        @Test
        @DisplayName("의무일보다 뒤에 활동했어도 이어진다 (다른 챌린지로 인증한 경우)")
        void consecutiveWhenLastCompletionIsAfterRequiredDay() {
            givenActiveUser();
            givenStreak(3, D10.minusDays(1));
            givenRequiredDay(D10.minusDays(3));

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(4);
        }

        @Test
        @DisplayName("마지막 완료일 뒤에 빼먹은 의무일이 있으면 1 로 리셋")
        void resetWhenRequiredDayMissed() {
            givenActiveUser();
            givenStreak(8, D10.minusDays(3));
            givenRequiredDay(D10.minusDays(1));

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(1);
            assertThat(user.getBestStreak()).as("최고 기록은 내려가지 않는다").isEqualTo(8);
        }

        @Test
        @DisplayName("직전 의무일이 없으면 이어진다 (오늘 시작한 챌린지의 첫 인증)")
        void continuesWhenNoRequiredDayExists() {
            givenActiveUser();
            givenStreak(10, D10.minusDays(1));
            givenRequiredDay(null);

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(11);
        }

        @Test
        @DisplayName("챌린지가 없던 기간을 건너뛰어도 이어진다")
        void continuesAcrossPeriodWithoutChallenge() {
            givenActiveUser();
            givenStreak(15, D10.minusMonths(2));
            givenRequiredDay(null);

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(16);
        }

        @Test
        @DisplayName("완료 이력도 의무일도 없으면 1")
        void firstCompletionWithoutRequiredDay() {
            givenActiveUser();
            givenRequiredDay(null);

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날 다시 호출되면 변화 없음")
        void idempotentPerDay() {
            givenActiveUser();
            givenStreak(4, D10);
            givenRequiredDay(D10.minusDays(1));

            userService.updateStreak(USER_ID, D10);

            assertThat(user.getPersonalStreak()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("전역 인증 스트릭 — 조회 시점 보정")
    class CalculateStreak {

        @Test
        @DisplayName("완료 이력이 없으면 0")
        void zeroWhenNeverCompleted() {
            givenActiveUser();
            givenTodayIs(D10);
            givenRequiredDay(D10.minusDays(1));

            assertThat(userService.getMyProfile(USER_ID).personalStreak()).isZero();
        }

        @Test
        @DisplayName("오늘 완료했으면 저장값 그대로")
        void storedValueWhenCompletedToday() {
            givenActiveUser();
            givenStreak(7, D10);
            givenTodayIs(D10);
            givenRequiredDay(D10.minusDays(1));

            assertThat(userService.getMyProfile(USER_ID).personalStreak()).isEqualTo(7);
        }

        @Test
        @DisplayName("의무일을 빼먹었으면 0 으로 보이고 저장값은 남는다")
        void zeroWhenRequiredDayMissed() {
            givenActiveUser();
            givenStreak(8, D10.minusDays(3));
            givenTodayIs(D10);
            givenRequiredDay(D10.minusDays(1));

            UserProfileResponse response = userService.getMyProfile(USER_ID);

            assertThat(response.personalStreak()).isZero();
            assertThat(user.getPersonalStreak()).as("저장값은 그대로 남는다").isEqualTo(8);
            assertThat(response.bestStreak()).as("최고 기록도 남는다").isEqualTo(8);
        }

        @Test
        @DisplayName("대상일이 아닌 날만 지났으면 저장값 유지")
        void storedValueWhenOnlyNonRequiredDaysPassed() {
            givenActiveUser();
            givenStreak(3, D10.minusDays(2));
            givenTodayIs(D10);
            givenRequiredDay(D10.minusDays(2));

            assertThat(userService.getMyProfile(USER_ID).personalStreak()).isEqualTo(3);
        }

        @Test
        @DisplayName("속한 챌린지가 없으면 저장값 유지")
        void storedValueWhenNoRequiredDayExists() {
            givenActiveUser();
            givenStreak(15, D10.minusMonths(2));
            givenTodayIs(D10);
            givenRequiredDay(null);

            assertThat(userService.getMyProfile(USER_ID).personalStreak()).isEqualTo(15);
        }
    }

    @Nested
    @DisplayName("타 도메인 공개 메서드")
    class PublicApi {

        @Test
        @DisplayName("닉네임 일괄 조회는 한 번의 쿼리로 끝난다")
        void findNicknames() {
            given(userRepository.findAllByIdIn(List.of(1L, 2L)))
                    .willReturn(List.of(
                            UserFixture.user(1L, "a@example.com", "성혁"), UserFixture.user(2L, "b@example.com", "철완")));

            assertThat(userService.findNicknames(List.of(1L, 2L)))
                    .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(1L, "성혁", 2L, "철완"));
        }

        @Test
        @DisplayName("빈 목록이면 쿼리를 치지 않는다")
        void findNicknamesSkipsQueryOnEmptyInput() {
            assertThat(userService.findNicknames(List.of())).isEmpty();
            verify(userRepository, org.mockito.Mockito.never()).findAllByIdIn(org.mockito.ArgumentMatchers.any());
        }
    }
}

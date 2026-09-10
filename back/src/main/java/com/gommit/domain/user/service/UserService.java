package com.gommit.domain.user.service;

import com.gommit.domain.challenge.service.ChallengeMemberService;
import com.gommit.domain.group.service.GroupService;
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
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final RefreshTokenService refreshTokenService;
    private final EmailTokenService emailTokenService;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;
    private final ChallengeMemberService challengeMemberService;
    private final BusinessClock businessClock;

    // 내 정보 조회
    public UserProfileResponse getMyProfile(Long userId) {
        return toUserProfileResponse(findNotDeleted(userId));
    }

    // 내 정보 수정
    @Transactional
    public UserProfileResponse updateMyProfile(Long userId, UpdateProfileRequest request) {
        if (request.hasNoField()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        User user = findNotDeleted(userId);

        if (request.nickname() != null) {
            if (userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
                throw new BusinessException(ErrorCode.NICKNAME_DUPLICATED);
            }
            user.updateNickname(request.nickname());
        }
        if (request.introduction() != null) {
            user.updateIntroduction(request.introduction().isBlank() ? null : request.introduction());
        }

        return toUserProfileResponse(user);
    }

    // 비밀번호 변경
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findNotDeleted(userId);
        verifyPassword(user, request.currentPassword());

        if (request.newPassword().equals(request.currentPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED);
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenService.revokeAll(userId);
        emailTokenService.revokeUnused(userId, EmailTokenType.PASSWORD_RESET);
    }

    // 회원 탈퇴
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = findNotDeleted(userId);
        if (user.getPassword() != null) {
            verifyPassword(user, request.password());
        }

        groupService.leaveAllGroupsOnAccountDeletion(userId);

        user.deleteAccount();
        authIdentityRepository.deleteByUserId(userId);
        refreshTokenService.revokeAll(userId);
    }

    // 이메일 사용 가능 여부
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    // 닉네임 사용 가능 여부
    public boolean isNicknameAvailable(String nickname) {
        return !userRepository.existsByNickname(nickname);
    }

    // 탈퇴하지 않은 사용자 조회
    private User findNotDeleted(Long userId) {
        return userRepository
                .findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // 비밀번호 확인
    private void verifyPassword(User user, String rawPassword) {
        if (rawPassword == null
                || user.getPassword() == null
                || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    // 프로필 응답 조립
    UserProfileResponse toUserProfileResponse(User user) {
        LocalDate today = businessClock.today();
        LocalDate lastRequiredCheckInDay = challengeMemberService.findLastRequiredCheckInDay(user.getId(), today);
        return new UserProfileResponse(user, user.calculateStreak(today, lastRequiredCheckInDay));
    }

    // 하루 인증 목표 완료 시 스트릭 갱신
    @Transactional
    public void updateStreak(Long userId, LocalDate businessDate) {
        LocalDate lastRequiredCheckInDay = challengeMemberService.findLastRequiredCheckInDay(userId, businessDate);
        findNotDeleted(userId).updateStreak(businessDate, lastRequiredCheckInDay);
    }

    // 닉네임 일괄 조회
    public Map<Long, String> findNicknames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(userIds).stream().collect(Collectors.toMap(User::getId, User::getNickname));
    }
}

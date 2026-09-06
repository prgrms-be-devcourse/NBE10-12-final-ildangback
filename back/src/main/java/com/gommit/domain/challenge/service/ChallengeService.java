package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.request.OwnerDelegationRequest;
import com.gommit.domain.challenge.dto.response.*;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final UserRepository userRepository;
    private final CheckInRepository checkInRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final ChallengeMemberService challengeMemberService;
    private final ChallengeProgressCalculator challengeProgressCalculator;

    @Transactional
    public Challenge createInitialChallenge(Long groupId, Long userId, InitialChallengeSettingRequest setting) {
        validateInitialChallengeSetting(setting);
        int requiredDayCount = calculateRequiredDayCount(
                setting.startDate(),
                setting.endDate(),
                setting.frequencyType(),
                setting.frequencyValue(),
                setting.daysOfWeek());

        // DB (String) 저장을 위해 List를 문자열로 변환
        String daysOfWeek = convertDaysOfWeek(setting.daysOfWeek());
        boolean allowPhoto = setting.allowedTypes().contains(CheckInType.PHOTO);
        Challenge challenge = Challenge.builder()
                .groupId(groupId)
                .seqNo(1)
                .startDate(setting.startDate())
                .endDate(setting.endDate())
                .frequencyType(setting.frequencyType())
                .frequencyValue(setting.frequencyValue())
                .daysOfWeek(daysOfWeek)
                .dailyCheckInCount(setting.dailyCheckInCount())
                .requiredDayCount(requiredDayCount)
                .groupCurrentStreak(0)
                .groupBestStreak(0)
                .allowPhoto(allowPhoto)
                .build();
        Challenge savedChallenge = challengeRepository.save(challenge);
        challengeMemberService.createChallengeMember(savedChallenge, userId, ChallengeMemberRole.OWNER);
        return savedChallenge;
    }

    @Transactional(readOnly = true)
    public ChallengeStatusResponse getChallengeStatus(Long challengeId, Long userId) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        ChallengeMember owner = challengeMemberRepository
                .findByChallengeIdAndRole(challengeId, ChallengeMemberRole.OWNER)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER));
        long participantCount =
                challengeMemberRepository.countByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE);
        int totalDays = challenge.getRequiredDayCount();
        LocalDate today = LocalDate.now();
        int currentDay = challengeProgressCalculator.calculateCurrentDay(challenge, today);
        double periodProgressRate = challengeProgressCalculator.calculatePeriodProgressRate(currentDay, totalDays);
        boolean checkInDay = isCheckInDay(challenge, today);
        ChallengeDetailResponse challengeDetailResponse = new ChallengeDetailResponse(challenge, owner.getUserId());
        int myCurrentCount = 0; // TODO: CheckIn 연동
        boolean myCompleted = myCurrentCount >= challenge.getDailyCheckInCount();
        // TODO: 연장 가능 기간 정책 적용
        boolean extensionAvailable = false;
        return new ChallengeStatusResponse(
                challengeDetailResponse,
                currentDay,
                totalDays,
                (int) participantCount,
                periodProgressRate,
                checkInDay,
                myCurrentCount,
                myCompleted,
                extensionAvailable);
    }

    @Transactional(readOnly = true)
    public List<MemberTodayStatusResponse> getMemberTodayStatuses(Long challengeId, Long userId) {
        challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        List<ChallengeMember> members =
                challengeMemberRepository.findAllByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE);
        List<Long> userIds = members.stream().map(ChallengeMember::getUserId).toList();
        List<User> users = userRepository.findAllByIdIn(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, user -> user));
        LocalDate today = LocalDate.now();
        return members.stream()
                .map(member -> {
                    User user = userMap.get(member.getUserId());
                    // 오늘 인증 횟수 조회
                    // TODO: CheckInRepository 연동 후 실제 값으로 변경
                    long todayCheckInCount = 0;
                    //            long todayCheckInCount =
                    // checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challengeId, member.getUserId(),
                    // today);
                    return new MemberTodayStatusResponse(
                            member.getUserId(), user.getNickname(), (int) todayCheckInCount);
                })
                .toList();
    }

    // 시즌 연장시 챌린지 설정 (OWNER만 수정 가능 / READY 상태에서만 설정 수정 가능)
    @Transactional
    public ChallengeUpdateResponse updateChallenge(Long challengeId, Long userId, ChallengeUpdateRequest request) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        ChallengeMember challengeMember = challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        if (challengeMember.getRole() != ChallengeMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER);
        }
        if (challenge.getStatus() != ChallengeStatus.READY) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_EDITABLE);
        }
        if (challenge.getSeqNo() > 1 && request.startDate() != null) {
            throw new BusinessException(ErrorCode.EXTENSION_START_DATE_NOT_EDITABLE);
        }
        LocalDate startDate = request.startDate() != null ? request.startDate() : challenge.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : challenge.getEndDate();
        if (!startDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_START_DATE);
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
        FrequencyType frequencyType =
                request.frequencyType() != null ? request.frequencyType() : challenge.getFrequencyType();
        Integer frequencyValue =
                request.frequencyValue() != null ? request.frequencyValue() : challenge.getFrequencyValue();
        List<DaysOfWeek> daysOfWeek;
        if (request.daysOfWeek() != null) {
            daysOfWeek = request.daysOfWeek();
        } else if (challenge.getDaysOfWeek() != null) {
            daysOfWeek = Arrays.stream(challenge.getDaysOfWeek().split(","))
                    .map(String::trim)
                    .map(DaysOfWeek::valueOf)
                    .toList();
        } else {
            daysOfWeek = null;
        }
        switch (frequencyType) {
            case DAILY -> {
                // 별도 검증 없음
            }
            case DAYS_OF_WEEK -> {
                if (daysOfWeek == null || daysOfWeek.isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
                }
            }
            case EVERY_N_DAYS -> {
                if (frequencyValue == null || frequencyValue < 2 || frequencyValue > 7) {
                    throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
                }
            }
        }
        int dailyCheckInCount =
                request.dailyCheckInCount() != null ? request.dailyCheckInCount() : challenge.getDailyCheckInCount();
        if (dailyCheckInCount < 1 || dailyCheckInCount > 10) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_COUNT);
        }
        List<CheckInType> allowedTypes = request.allowedTypes() != null
                ? request.allowedTypes()
                : challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of();
        if (allowedTypes.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CHECK_IN_METHOD);
        }
        boolean allowPhoto = allowedTypes.contains(CheckInType.PHOTO);
        int requiredDayCount = calculateRequiredDayCount(startDate, endDate, frequencyType, frequencyValue, daysOfWeek);
        String dayOfWeekValue = convertDaysOfWeek(daysOfWeek);
        challenge.updateSettings(
                startDate,
                endDate,
                frequencyType,
                frequencyValue,
                dayOfWeekValue,
                dailyCheckInCount,
                requiredDayCount,
                allowPhoto);
        return new ChallengeUpdateResponse(
                challenge.getId(),
                startDate,
                endDate,
                frequencyType,
                frequencyValue,
                daysOfWeek,
                dailyCheckInCount,
                allowedTypes);
    }

    @Transactional
    public OwnerDelegationResponse delegateOwner(Long challengeId, Long userId, OwnerDelegationRequest request) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        ChallengeMember currentMember = challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        if (currentMember.getRole() != ChallengeMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_OWNER);
        }
        if (userId.equals(request.targetUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }
        ChallengeMember targetMember = challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, request.targetUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));
        if (targetMember.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER);
        }
        currentMember.changeRole(ChallengeMemberRole.MEMBER);
        targetMember.changeRole(ChallengeMemberRole.OWNER);
        if (challenge.getSeqNo() == 1 || challenge.getStatus() == ChallengeStatus.ACTIVE) {
            ChallengeGroup group = challengeGroupRepository
                    .findById(challenge.getGroupId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
            group.changeOwner(request.targetUserId());
        }
        return new OwnerDelegationResponse(challengeId, userId, request.targetUserId());
    }

    // 선택된 요일 DB 저장용 문자열로 변환
    private String convertDaysOfWeek(List<DaysOfWeek> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return null;
        }
        return daysOfWeek.stream().map(DaysOfWeek::name).collect(Collectors.joining(","));
    }

    private void validateInitialChallengeSetting(InitialChallengeSettingRequest setting) {
        validateStartDate(setting.startDate());
        validatePeriod(setting.startDate(), setting.endDate());
        validateWeekdays(setting);
        validateFrequencyValue(setting);
        validateAllowTypes(setting.allowedTypes());
    }

    private void validateStartDate(LocalDate startDate) {
        if (!startDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_START_DATE);
        }
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    private void validateWeekdays(InitialChallengeSettingRequest setting) {
        if (setting.frequencyType() == FrequencyType.DAYS_OF_WEEK
                && (setting.daysOfWeek() == null || setting.daysOfWeek().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
        }
    }

    private void validateFrequencyValue(InitialChallengeSettingRequest setting) {
        if (setting.frequencyType() == FrequencyType.EVERY_N_DAYS
                && (setting.frequencyValue() == null || setting.frequencyValue() <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
        }
    }

    private void validateAllowTypes(List<CheckInType> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CHECK_IN_METHOD);
        }
    }

    private boolean isCheckInDay(Challenge challenge, LocalDate today) {
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return false;
        }
        if (today.isBefore(challenge.getStartDate()) || today.isAfter(challenge.getEndDate())) {
            return false;
        }
        return switch (challenge.getFrequencyType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK ->
                Arrays.stream(challenge.getDaysOfWeek().split(","))
                        .map(DaysOfWeek::valueOf)
                        .anyMatch(daysOfWeek -> daysOfWeek == DaysOfWeek.getDaysOfWeek(today.getDayOfWeek()));
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);
                yield days % challenge.getFrequencyValue() == 0;
            }
        };
    }

    private int calculateRequiredDayCount(
            LocalDate startDate,
            LocalDate endDate,
            FrequencyType frequencyType,
            Integer frequencyValue,
            List<DaysOfWeek> daysOfWeek) {
        return switch (frequencyType) {
            case DAILY -> (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
            case DAYS_OF_WEEK -> {
                int count = 0;
                LocalDate date = startDate;
                while(!date.isAfter(endDate)) {
                    DaysOfWeek currentDay = DaysOfWeek.getDaysOfWeek(date.getDayOfWeek());
                    if(daysOfWeek.contains(currentDay)) {
                        count++;
                    }
                    date = date.plusDays(1);
                }
                yield count;
            }
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(startDate, endDate);
                yield (int) (days / frequencyValue) + 1;
            }
        };
    }
}

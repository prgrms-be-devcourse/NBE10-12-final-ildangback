package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.ExtensionChoiceRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.request.OwnerDelegationRequest;
import com.gommit.domain.challenge.dto.response.*;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.AlreadyBuiltException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final UserRepository userRepository;
    private final CheckInRepository checkInRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;

    // 그룹 생성 시 첫 챌린지 생성
    public Challenge createInitialChallenge(Long groupId, Long userId,InitialChallengeSettingRequest setting) {

        // 유효성 체크
        validateInitialChallengeSetting(setting);

        // 챌린지 기간 동안 실제 인증해야하는 날짜 수 계산
        int requiredDayCount = calculateRequiredDayCount(setting);

        // DB (String) 저장을 위해 List를 문자열로 변환
        String daysOfWeek = convertDaysOfWeek(setting.daysOfWeek());

        // 허용된 인증 방식 PHOTO가 포함되어 있는지 확인
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

        createChallengeMember(savedChallenge, userId, ChallengeMemberRole.OWNER);

        return savedChallenge;
    }

    // 그룹 생성자를 챌린지멤버 등록
    public ChallengeMember createChallengeMember(Challenge challenge, Long userId, ChallengeMemberRole role) {
        ChallengeMember challengeMember = ChallengeMember.builder()
            .challenge(challenge)
            .userId(userId)
            .role(role)
            .build();

        return challengeMemberRepository.save(challengeMember);
    }


    // 챌린지 현황
    @Transactional(readOnly = true)
    public ChallengeStatusResponse getChallengeStatus(Long challengeId, Long userId) {
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 시즌 멤버 확인
        ChallengeMember myMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        // OWNER 체크
        ChallengeMember owner = challengeMemberRepository.findByChallengeIdAndRole(challengeId, ChallengeMemberRole.OWNER).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_OWNER));

        long participantCount = challengeMemberRepository.countByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE);

        // 전체 인증 예정일 수
        int totalDays = challenge.getRequiredDayCount();

        LocalDate today = LocalDate.now();

        // 현재까지 인증 예정일 수
        int currentDay = calculateCurrentDay(challenge, today);

        // 인증 예정일 기준 진행률
        double periodProgressRate = calculatePeriodProgressRate(currentDay, totalDays);

        // 오늘이 인증일인지 확인
        boolean checkInDay = isCheckInDay(challenge, today);

        // 챌린지 상세 응답
        ChallengeDetailResponse challengeDetailResponse =
            new ChallengeDetailResponse(
                challenge,
                owner.getUserId()
            );

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
            extensionAvailable
        );
    }

    // 시즌 멤버 오늘 인증 현황
    @Transactional(readOnly = true)
    public List<MemberTodayStatusResponse> getMemberTodayStatuses(
        Long challengeId,
        Long userId
    ) {
        // 챌린지 조회
        Challenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND)
            );

        // 요청자가 해당 시즌 멤버인지 확인
        challengeMemberRepository
            .findByChallengeIdAndUserId(challengeId, userId)
            .orElseThrow(() ->
                new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER)
            );

        // 현재 참여 중인 시즌 멤버 조회
        List<ChallengeMember> members =
            challengeMemberRepository.findAllByChallengeIdAndStatus(
                challengeId,
                ChallengeMemberStatus.ACTIVE
            );

        // 시즌 멤버 userId 목록
        List<Long> userIds = members.stream()
            .map(ChallengeMember::getUserId)
            .toList();

        // 유저 정보 한 번에 조회
        List<User> users = userRepository.findAllByIdIn(userIds);

        // userId로 유저를 찾을 수 있도록 Map 변환
        Map<Long, User> userMap = users.stream()
            .collect(Collectors.toMap(
                User::getId,
                user -> user
            ));

        LocalDate today = LocalDate.now();

        return members.stream().map(member -> {
            User user = userMap.get(member.getUserId());

            // 오늘 인증 횟수 조회
            long todayCheckInCount = checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(
                challengeId,
                member.getUserId(),
                today
            );

            return new MemberTodayStatusResponse(
                member.getUserId(),
                user.getNickname(),
                (int) todayCheckInCount
            );
        }).toList();
    }

    // 시즌 연장시 챌린지 설정 (OWNER만 수정 가능 / READY 상태에서만 설정 수정 가능)
    @Transactional
    public ChallengeUpdateResponse updateChallenge(Long challengeId, Long userId, ChallengeUpdateRequest request) {
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        ChallengeMember challengeMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        if(challengeMember.getRole() != ChallengeMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_OWNER);
        }

        if(challenge.getStatus() != ChallengeStatus.READY) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_EDITABLE);
        }

        /*
        * 날짜 최종값
        * */

        LocalDate startDate = request.startDate() != null ? request.startDate() : challenge.getStartDate();
        LocalDate endDate = request.endDate() != null ? request.endDate() : challenge.getEndDate();

        if(!startDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_START_DATE);
        }

        // 연장 시즌이면 이전 시즌 종료일 이후여야 함
        if(challenge.getSeqNo() > 1) {
            Challenge previousChallenge = challengeRepository.findByGroupIdAndSeqNo(challenge.getGroupId(), challenge.getSeqNo() - 1).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

            if(!startDate.isAfter(previousChallenge.getEndDate())) {
                throw new BusinessException(ErrorCode.INVALID_EXTENSION_START_DATE);
            }
        }

        if(endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }

        /*
         * 인증 주기 최종값
         * */

        FrequencyType frequencyType = request.frequencyType() != null ? request.frequencyType() : challenge.getFrequencyType();
        Integer frequencyValue = request.frequencyValue() != null ? request.frequencyValue() : challenge.getFrequencyValue();

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
                if (frequencyValue == null
                    || frequencyValue < 2
                    || frequencyValue > 7) {
                    throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
                }
            }
        }

        /*
         * 하루 인증 횟수
         * */

        int dailyCheckInCount = request.dailyCheckInCount() != null
            ? request.dailyCheckInCount()
            : challenge.getDailyCheckInCount();

        if (dailyCheckInCount < 1 || dailyCheckInCount > 10) {
            throw new BusinessException(ErrorCode.INVALID_DAILY_COUNT);
        }

        /*
         * 인증 방법
         * */
        List<CheckInType> allowedTypes = request.allowedTypes() != null ? request.allowedTypes() : challenge.isAllowPhoto() ? List.of(CheckInType.PHOTO) : List.of();

        if(allowedTypes.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CHECK_IN_METHOD);
        }

        boolean allowPhoto = allowedTypes.contains(CheckInType.PHOTO);

        int requiredDayCount = switch (frequencyType) {
            case DAILY ->
                (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;

            case DAYS_OF_WEEK -> {
                int count = 0;

                for (
                    LocalDate date = startDate;
                    !date.isAfter(endDate);
                    date = date.plusDays(1)
                ) {
                    if (daysOfWeek.contains(
                        DaysOfWeek.getDaysOfWeek(date.getDayOfWeek())
                    )) {
                        count++;
                    }
                }

                yield count;
            }

            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(startDate, endDate);

                yield (int) (days / frequencyValue) + 1;
            }
        };

        String dayOfWeekValue = daysOfWeek == null ? null : daysOfWeek.stream().map(DaysOfWeek::name).collect(Collectors.joining(","));

        challenge.updateSettings(startDate, endDate, frequencyType, frequencyValue,dayOfWeekValue, dailyCheckInCount, requiredDayCount, allowPhoto);

        return new ChallengeUpdateResponse(challenge.getId(), startDate, endDate, frequencyType, frequencyValue, daysOfWeek, dailyCheckInCount, allowedTypes);
    }

    @Transactional
    public OwnerDelegationResponse delegateOwner(Long challengeId, Long userId, OwnerDelegationRequest request) {
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        ChallengeMember currentMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        // OWNER인지 확인
        if(currentMember.getRole() != ChallengeMemberRole.OWNER) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_OWNER);
        }

        if(userId.equals(request.targetUserId())) {
            throw new BusinessException(ErrorCode.CANNOT_DELEGATE_TO_SELF);
        }

        // 위임 대상 멤버 조회
        ChallengeMember targetMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, request.targetUserId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        if(targetMember.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        currentMember.changeRole(ChallengeMemberRole.MEMBER);
        targetMember.changeRole(ChallengeMemberRole.OWNER);

        if(challenge.getSeqNo() == 1 || challenge.getStatus() == ChallengeStatus.ACTIVE) {
            ChallengeGroup group = challengeGroupRepository.findById(challenge.getGroupId()).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
            group.changeOwner(request.targetUserId());
        }

        return new OwnerDelegationResponse(
            challengeId,
            userId,
            request.targetUserId()
        );
    }

    // 다음 시즌 연장 참여 의사 검사
    @Transactional
    public ExtensionChoiceResponse updateExtensionChoice(Long challengeId, Long userId,ExtensionChoiceRequest request) {
        // 챌린지 조회
        Challenge challenge = challengeRepository.findById(challengeId).orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));

        // 진해 중인 시즌에서만 연장 의사 선택 가능
        if(challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_NOT_AVAILABLE);
        }

        // 해당 챌린지의 내 참여 정보 조회
        ChallengeMember challengeMember = challengeMemberRepository.findByChallengeIdAndUserId(challengeId, userId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER));

        // 현재 참여 중인 멤버만 연장 의사 선택 가능
        if(challengeMember.getStatus() != ChallengeMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_CHALLENGE_MEMBER);
        }

        // 연장 투표 마감 여부 확인
        validateExtensionChoicePeriod(challenge);

        // EXTEND / DECLINE 선택
        validateExtensionChoice(request.choice());

        // 내 연장 의사 변경
        challengeMember.changeExtensionChoice(request.choice());

        // 현재 ACTIVE 멤버들의 선택 현황 집계
        int pendingCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.PENDING);
        int extendCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.EXTEND);
        int declineCount = (int) challengeMemberRepository.countByChallengeIdAndStatusAndExtensionChoice(challengeId, ChallengeMemberStatus.ACTIVE, ExtensionChoice.DECLINE);

        return new ExtensionChoiceResponse(
            challengeId,
            userId,
            challengeMember.getExtensionChoice(),
            pendingCount,
            extendCount,
            declineCount
        );
    }


    // 선택된 요일 DB 저장용 문자열로 변환
    private String convertDaysOfWeek(List<DaysOfWeek> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return null;
        }

        return daysOfWeek.stream().map(DaysOfWeek::name).collect(Collectors.joining(","));
    }

    // 인증 주기 계산
    private int calculateRequiredDayCount(
        InitialChallengeSettingRequest setting
    ) {
        return switch (setting.frequencyType()) {
            // 매일인증
            case DAILY -> (int) ChronoUnit.DAYS.between(setting.startDate(), setting.endDate()) + 1;
            // 선택된 요일 인증
            case DAYS_OF_WEEK -> {
                int count = 0;
                for(
                    LocalDate date = setting.startDate();
                    !date.isAfter(setting.endDate());
                    date = date.plusDays(1)
                ) {
                    if(setting.daysOfWeek().contains(DaysOfWeek.getDaysOfWeek(date.getDayOfWeek()))) {
                        count++;
                    }
                } yield  count;
            }
            // N일마다 인증
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(setting.startDate(), setting.endDate());

                yield (int) (days / setting.frequencyValue()) + 1;
            }
        };
    }

    private void validateInitialChallengeSetting(
        InitialChallengeSettingRequest setting
    ) {
        // 챌린지 시작일 검증(당일시작은 불가능)
        validateStartDate(setting.startDate());
        // 챌린지 기간 검증
        validatePeriod(setting.startDate(), setting.endDate());
        // WEEKDAYS 검증
        validateWeekdays(setting);
        // N일마다 검증
        validateFrequencyValue(setting);
        // 인증방식 검증
        validateAllowTypes(setting.allowedTypes());
    }

    // 챌린지 시작일 검증
    private void validateStartDate(LocalDate startDate) {
        if(!startDate.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_START_DATE);
        }
    }

    // 챌린지 종료일 검증
    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if(endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.INVALID_PERIOD);
        }
    }

    // WEEKDAYS 필수 검증 체크
    private void validateWeekdays(InitialChallengeSettingRequest setting) {
        if (setting.frequencyType() == FrequencyType.DAYS_OF_WEEK && (setting.daysOfWeek() == null || setting.daysOfWeek().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
        }
    }

    // N일마다 필수 검증 체크
    private void validateFrequencyValue(InitialChallengeSettingRequest setting) {
        if (setting.frequencyType() == FrequencyType.EVERY_N_DAYS && (setting.frequencyValue() == null || setting.frequencyValue() <= 0)) {
            throw new BusinessException(ErrorCode.INVALID_FREQUENCY);
        }
    }

    // 인증방식 검증 체크
    private void validateAllowTypes(List<CheckInType> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_CHECK_IN_METHOD);
        }
    }

    // 챌린지 예정일 계산
    private int calculateCurrentDay(Challenge challenge, LocalDate today) {

        // 시작 전 챌린지
        if(challenge.getStatus() ==  ChallengeStatus.READY) {
            return 0;
        }

        // 종료된 챌린지
        if(challenge.getStatus() == ChallengeStatus.ENDED) {
            return challenge.getRequiredDayCount();
        }

        return switch (challenge.getFrequencyType()) {
            case DAILY -> calculateDailyCurrentDay(challenge, today);
            case DAYS_OF_WEEK -> calculateDaysOfWeekCurrentDay(challenge, today);
            case EVERY_N_DAYS -> calculateEveryNDaysCurrentDay(challenge, today);
        };
    }

    private int calculateDailyCurrentDay(Challenge challenge, LocalDate today) {
        // 스케줄러 꼬일경우 방지
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        int currentDay = (int) ChronoUnit.DAYS.between(challenge.getStartDate(), today) + 1;

        return Math.min(currentDay, challenge.getRequiredDayCount());
    }

    private int calculateDaysOfWeekCurrentDay(Challenge challenge, LocalDate today) {
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        List<DaysOfWeek> scheduledDays = Arrays.stream(challenge.getDaysOfWeek().split(",")).map(DaysOfWeek::valueOf).toList();

        LocalDate endDate = today.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : today;

        int count = 0;

        for(LocalDate date = challenge.getStartDate();
            !date.isAfter(endDate);
            date = date.plusDays(1)) {
            DaysOfWeek currentDayOfWeek = DaysOfWeek.getDaysOfWeek(date.getDayOfWeek());

            if(scheduledDays.contains(currentDayOfWeek)) {
                count++;
            }
        }
        return count;
    }

    private int calculateEveryNDaysCurrentDay(Challenge challenge, LocalDate today) {
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);

        return (int) (days / challenge.getFrequencyValue()) + 1;
    }

    private double calculatePeriodProgressRate(int currentDay, int totalDays) {
        if(totalDays == 0) {
            return 0.0;
        }

        return Math.round(((double)currentDay / totalDays) * 1000) / 10.0;
    }

    private boolean isCheckInDay(Challenge challenge, LocalDate today) {
        // 진행 중 챌린지가 아니면 인증일이 아님
        if(challenge.getStatus() != ChallengeStatus.ACTIVE) {
            return false;
        }

        // 챌린지 기간 밖이면 인증일이 아님
        if(today.isBefore(challenge.getStartDate()) || today.isAfter(challenge.getEndDate())) {
            return false;
        }

        return switch (challenge.getFrequencyType()) {
            case DAILY -> true;
            case DAYS_OF_WEEK -> Arrays.stream(challenge.getDaysOfWeek().split(","))
                .map(DaysOfWeek::valueOf)
                .anyMatch(daysOfWeek -> daysOfWeek == DaysOfWeek.getDaysOfWeek(today.getDayOfWeek()));
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);

                yield days % challenge.getFrequencyValue() == 0;
            }
        };
    }

    private void validateExtensionChoice(ExtensionChoice choice) {
        if(choice != ExtensionChoice.EXTEND && choice != ExtensionChoice.DECLINE) {
            throw new BusinessException(ErrorCode.INVALID_EXTENSION_CHOICE);
        }
    }

    private void validateExtensionChoicePeriod(Challenge challenge) {
        LocalDate deadline = challenge.getEndDate().minusDays(2);

        if(LocalDate.now().isAfter(deadline)) {
            throw new BusinessException(ErrorCode.EXTENSION_CHOICE_CLOSED);
        }
    }

}

package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.response.ChallengeDetailResponse;
import com.gommit.domain.challenge.dto.response.ChallengeStatusResponse;
import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.user.entity.User;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    // 그룹 생성 시 첫 챌린지 생성
    public Challenge createInitialChallenge(Long groupId, Long userId,InitialChallengeSettingRequest setting) {

        // 유효성 체크
        validateInitialChallengeSetting(setting);

        // 챌린지 기간 동안 실제 인증해야하는 날짜 수 계산
        int requiredDayCount = calculateRequiredDayCount(setting);

        // DB (String) 저장을 위해 List를 문자열로 변환
        String weekdays = convertWeekdays(setting.weekdays());

        // 허용된 인증 방식 PHOTO가 포함되어 있는지 확인
        boolean allowPhoto = setting.allowedTypes().contains(CheckInType.PHOTO);

        Challenge challenge = Challenge.builder()
            .groupId(groupId)
            .seqNo(1)
            .startDate(setting.startDate())
            .endDate(setting.endDate())
            .frequencyType(setting.frequencyType())
            .frequencyValue(setting.frequencyValue())
            .weekdays(weekdays)
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

    // 선택된 요일 DB 저장용 문자열로 변환
    private String convertWeekdays(List<Weekday> weekdays) {
        if (weekdays == null || weekdays.isEmpty()) {
            return null;
        }

        return weekdays.stream().map(Weekday::name).collect(Collectors.joining(","));
    }

    // 인증 주기 계산
    private int calculateRequiredDayCount(
        InitialChallengeSettingRequest setting
    ) {
        return switch (setting.frequencyType()) {
            // 매일인증
            case DAILY -> (int) ChronoUnit.DAYS.between(setting.startDate(), setting.endDate()) + 1;
            // 선택된 요일 인증
            case WEEKDAYS -> {
                int count = 0;
                for(
                    LocalDate date = setting.startDate();
                    !date.isAfter(setting.endDate());
                    date = date.plusDays(1)
                ) {
                    if(setting.weekdays().contains(Weekday.getWeekday(date.getDayOfWeek()))) {
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
        if (setting.frequencyType() == FrequencyType.WEEKDAYS && (setting.weekdays() == null || setting.weekdays().isEmpty())) {
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
            case WEEKDAYS -> calculateWeekdaysCurrentDay(challenge, today);
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

    private int calculateWeekdaysCurrentDay(Challenge challenge, LocalDate today) {
        if(today.isBefore(challenge.getStartDate())) {
            return 0;
        }

        List<Weekday> weekdays = Arrays.stream(challenge.getWeekdays().split(",")).map(Weekday::valueOf).toList();

        LocalDate endDate = today.isAfter(challenge.getEndDate()) ? challenge.getEndDate() : today;

        int count = 0;

        for(LocalDate date = challenge.getStartDate();
            !date.isAfter(endDate);
            date = date.plusDays(1)) {
            Weekday weekday = Weekday.getWeekday(date.getDayOfWeek());

            if(weekdays.contains(weekday)) {
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
            case WEEKDAYS -> Arrays.stream(challenge.getWeekdays().split(","))
                .map(Weekday::valueOf)
                .anyMatch(weekday -> weekday == Weekday.getWeekday(today.getDayOfWeek()));
            case EVERY_N_DAYS -> {
                long days = ChronoUnit.DAYS.between(challenge.getStartDate(), today);

                yield days % challenge.getFrequencyValue() == 0;
            }
        };
    }
}

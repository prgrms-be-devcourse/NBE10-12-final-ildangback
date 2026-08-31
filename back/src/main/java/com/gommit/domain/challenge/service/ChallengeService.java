package com.gommit.domain.challenge.service;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.challenge.entity.Weekday;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
            .startDate(setting.startDate())
            .endDate(setting.endDate())
            .frequencyType(setting.frequencyType())
            .frequencyValue(setting.frequencyValue())
            .weekdays(weekdays)
            .dailyCheckInCount(setting.dailyCheckInCount())
            .requiredDayCount(requiredDayCount)
            .allowPhoto(allowPhoto)
            .build();

        Challenge savedChallenge = challengeRepository.save(challenge);

        createChallengeOwner(savedChallenge, userId);

        return savedChallenge;
    }

    // 그룹 생성자를 첫 챌린지의 OWNER로 등록
    private void createChallengeOwner(Challenge challenge, Long userId) {
        ChallengeMember challengeMember = ChallengeMember.builder()
            .challenge(challenge)
            .userId(userId)
            .build();

        challengeMemberRepository.save(challengeMember);
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
                    if(setting.weekdays().contains(Weekday.valueOf(date.getDayOfWeek().name()))) {
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
}

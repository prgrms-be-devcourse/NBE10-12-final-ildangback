package com.gommit.domain.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeExtensionService;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.entity.*;
import com.gommit.domain.group.service.GroupService;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ChallengeSeasonIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private GroupService groupService;

    @Autowired
    private ChallengeService challengeService;

    @Autowired
    private ChallengeExtensionService extensionService;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Test
    @DisplayName("실제 최초 계산, 연장 확정, OWNER 요일 수정으로 인증일 수가 5 → 4 → 9가 된다")
    void recalculatesNextSeasonAndOwnerSettings() {
        loginAs();
        Long ownerId =
                jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, "tester@example.com");
        LocalDate today = LocalDate.of(2026, 8, 31);
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        // 날짜 검증만 고정한다. 서비스, Calculator, Repository는 실제 Spring 빈이다.
        try (var dates = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            dates.when(LocalDate::now).thenReturn(today);
            groupService.createGroup(
                    ownerId,
                    new GroupCreateRequest(
                            "시즌 연장",
                            "인증",
                            GroupCategory.EXERCISE,
                            MapType.GYM,
                            Visibility.PUBLIC,
                            6,
                            new InitialChallengeSettingRequest(
                                    start,
                                    end,
                                    FrequencyType.DAYS_OF_WEEK,
                                    null,
                                    List.of(DaysOfWeek.WED),
                                    1,
                                    List.of(CheckInType.PHOTO))));
            Long groupId = jdbcTemplate.queryForObject(
                    "select id from challenge_groups where owner_id = ?", Long.class, ownerId);
            Challenge current =
                    challengeRepository.findByGroupIdAndSeqNo(groupId, 1).orElseThrow();
            assertThat(current.getRequiredDayCount()).isEqualTo(5);
            jdbcTemplate.update("update challenges set status = 'ACTIVE' where id = ?", current.getId());
            jdbcTemplate.update(
                    "update challenge_members set extension_choice = 'EXTEND' where challenge_id = ? and user_id = ?",
                    current.getId(),
                    ownerId);

            extensionService.finalizeExtension(current.getId());

            Challenge next =
                    challengeRepository.findByGroupIdAndSeqNo(groupId, 2).orElseThrow();
            assertThat(next.getStatus()).isEqualTo(ChallengeStatus.READY);
            assertThat(next.getStartDate()).isEqualTo(end.plusDays(1));
            assertThat(next.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 30));
            assertThat(ChronoUnit.DAYS.between(next.getStartDate(), next.getEndDate()))
                    .isEqualTo(ChronoUnit.DAYS.between(start, end));
            assertThat(next.getFrequencyType()).isEqualTo(current.getFrequencyType());
            assertThat(next.getFrequencyValue()).isEqualTo(current.getFrequencyValue());
            assertThat(next.getDaysOfWeek()).isEqualTo("WED");
            assertThat(next.getRequiredDayCount()).isEqualTo(4);
            assertThat(next.getRequiredDayCount()).isNotEqualTo(current.getRequiredDayCount());

            challengeService.updateChallenge(
                    next.getId(),
                    ownerId,
                    new ChallengeUpdateRequest(
                            null, null, null, null, List.of(DaysOfWeek.WED, DaysOfWeek.FRI), null, null));

            Challenge updated = challengeRepository.findById(next.getId()).orElseThrow();
            assertThat(updated.getRequiredDayCount()).isEqualTo(9);
            assertThat(updated.getDaysOfWeek()).isEqualTo("WED,FRI");
            assertThat(updated.getStartDate()).isEqualTo(next.getStartDate());
            assertThat(updated.getEndDate()).isEqualTo(next.getEndDate());
            assertThat(challengeRepository
                            .findById(current.getId())
                            .orElseThrow()
                            .getRequiredDayCount())
                    .isEqualTo(5);
        }
    }
}

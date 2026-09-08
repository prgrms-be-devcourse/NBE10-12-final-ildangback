package com.gommit.domain.group;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.entity.FrequencyType;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.entity.*;
import com.gommit.domain.group.service.GroupService;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GroupJoinConcurrencyIntegrationTest extends IntegrationTestSupport {
    @Autowired
    private GroupService groupService;

    @Test
    @DisplayName("MySQL 트랜잭션에서 마지막 한 자리를 동시에 신청하면 한 명만 성공하고 나머지는 GROUP_FULL")
    void onlyAvailableMembersCanJoinConcurrently() throws Exception {
        loginAs();
        Long ownerId = userId("tester@example.com");
        groupService.createGroup(
                ownerId,
                new GroupCreateRequest(
                        "동시 참여",
                        "인증",
                        GroupCategory.EXERCISE,
                        MapType.GYM,
                        Visibility.PUBLIC,
                        3,
                        new InitialChallengeSettingRequest(
                                LocalDate.now().plusDays(1),
                                LocalDate.now().plusDays(7),
                                FrequencyType.DAILY,
                                null,
                                null,
                                1,
                                List.of(CheckInType.PHOTO))));
        Long groupId =
                jdbcTemplate.queryForObject("select id from challenge_groups where owner_id = ?", Long.class, ownerId);
        loginAs("existing@example.com", "기존멤버");
        groupService.joinGroup(groupId, userId("existing@example.com"));
        List<Long> applicants = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = "applicant" + i + "@example.com";
            loginAs(email, "신청자" + i);
            applicants.add(userId(email));
        }
        // 테스트 전체에 트랜잭션을 걸지 않아 준비 데이터가 worker 시작 전에 커밋된다.
        assertThat(activeMembers(groupId)).isEqualTo(2);
        CountDownLatch ready = new CountDownLatch(applicants.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(applicants.size());
        List<Future<ErrorCode>> futures = new ArrayList<>();
        try {
            for (Long applicant : applicants) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 시작 대기 시간 초과");
                    }
                    try {
                        groupService.joinGroup(groupId, applicant);
                        return null;
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<ErrorCode> results = new ArrayList<>();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(results.stream().filter(result -> result == null).count())
                    .isEqualTo(1);
            assertThat(results.stream().filter(result -> result != null).toList())
                    .hasSize(5)
                    .containsOnly(ErrorCode.GROUP_FULL);
            assertThat(activeMembers(groupId)).isEqualTo(3);
            assertThat(jdbcTemplate.queryForObject(
                            "select count(*) from challenge_members cm join challenges c on cm.challenge_id = c.id where c.group_id = ? and cm.status = 'ACTIVE'",
                            Long.class,
                            groupId))
                    .isEqualTo(3);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Long userId(String email) {
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private long activeMembers(Long groupId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from group_members where group_id = ? and status = 'ACTIVE'", Long.class, groupId);
    }
}

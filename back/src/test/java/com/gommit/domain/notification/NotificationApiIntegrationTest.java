package com.gommit.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gommit.domain.notification.entity.Notification;
import com.gommit.domain.notification.entity.NotificationType;
import com.gommit.domain.notification.repository.NotificationRepository;
import com.gommit.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationApiIntegrationTest extends IntegrationTestSupport {
    @Autowired
    NotificationRepository repository;

    private Long userId(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Notification create(Long userId) {
        return repository.saveAndFlush(new Notification(
                userId, NotificationType.CHECK_IN_NUDGE, "콕 찌르기가 도착했어요!", "왕왕왕님이 오늘 인증을 기다리고 있어요.", 106L));
    }

    @Test
    void listsOnlyOwnNotificationsNewestFirst() throws Exception {
        var tokens = loginAs();
        loginAs("other@example.com", "다른사용자");
        var older = create(userId("tester@example.com"));
        var newer = create(userId("tester@example.com"));
        create(userId("other@example.com"));
        var read = create(userId("tester@example.com"));
        read.read();
        repository.saveAndFlush(read);
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?", "2026-09-12 15:00:00", older.getId());
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?", "2026-09-13 15:00:00", newer.getId());
        mockMvc.perform(withToken(get("/api/notifications"), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(newer.getId()))
                .andExpect(jsonPath("$[1].id").value(older.getId()))
                .andExpect(jsonPath("$[0].type").value("CHECK_IN_NUDGE"))
                .andExpect(jsonPath("$[0].title").value(newer.getTitle()))
                .andExpect(jsonPath("$[0].body").value(newer.getBody()))
                .andExpect(jsonPath("$[0].refId").value(106))
                .andExpect(jsonPath("$[0].readAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].createdAt").value("2026-09-13T15:00:00"));
    }

    @Test
    void readsOwnNotificationIdempotently() throws Exception {
        var tokens = loginAs();
        var notification = create(userId("tester@example.com"));
        assertThat(notification.getReadAt()).isNull();
        mockMvc.perform(withToken(patch("/api/notifications/{id}/read", notification.getId()), tokens.accessToken()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        var readAt = repository.findById(notification.getId()).orElseThrow().getReadAt();
        assertThat(readAt).isNotNull();
        mockMvc.perform(withToken(get("/api/notifications"), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(withToken(patch("/api/notifications/{id}/read", notification.getId()), tokens.accessToken()))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(notification.getId()).orElseThrow().getReadAt())
                .isEqualTo(readAt);
    }

    @Test
    void rejectsMissingAndForeignNotifications() throws Exception {
        var tokens = loginAs();
        loginAs("other@example.com", "다른사용자");
        var foreign = create(userId("other@example.com"));
        for (Long id : new Long[] {Long.MAX_VALUE, foreign.getId()}) {
            mockMvc.perform(withToken(patch("/api/notifications/{id}/read", id), tokens.accessToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        }
        assertThat(repository.findById(foreign.getId()).orElseThrow().getReadAt())
                .isNull();
    }

    @Test
    void emptyListAndAuthentication() throws Exception {
        var tokens = loginAs();
        mockMvc.perform(withToken(get("/api/notifications"), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/1/read")).andExpect(status().isUnauthorized());
    }

    @Test
    void equalCreatedAtUsesDescendingId() throws Exception {
        var tokens = loginAs();
        var first = create(userId("tester@example.com"));
        var second = create(userId("tester@example.com"));
        jdbcTemplate.update("UPDATE notifications SET created_at = ?", "2026-09-13 15:00:00");
        mockMvc.perform(withToken(get("/api/notifications"), tokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].id").value(first.getId()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "2026-09-13T03:59:59,false",
        "2026-09-13T04:00:00,true",
        "2026-09-14T03:59:59,true",
        "2026-09-14T04:00:00,false"
    })
    void duplicateWindowIsStartInclusiveEndExclusive(String timestamp, boolean expected) {
        loginAs();
        var id = userId("tester@example.com");
        var notification = create(id);
        // Reading does not allow another nudge in the same business day.
        notification.read();
        repository.saveAndFlush(notification);
        jdbcTemplate.update(
                "UPDATE notifications SET created_at = ? WHERE id = ?",
                java.time.LocalDateTime.parse(timestamp),
                notification.getId());
        var start = java.time.LocalDate.of(2026, 9, 13).atTime(4, 0);
        assertThat(repository.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        id, NotificationType.CHECK_IN_NUDGE, 106L, start, start.plusDays(1)))
                .isEqualTo(expected);
        assertThat(repository.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        id, NotificationType.CHECK_IN_NUDGE, 107L, start, start.plusDays(1)))
                .isFalse();
        assertThat(repository.existsByUserIdAndTypeAndRefIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        id + 1, NotificationType.CHECK_IN_NUDGE, 106L, start, start.plusDays(1)))
                .isFalse();
    }
}

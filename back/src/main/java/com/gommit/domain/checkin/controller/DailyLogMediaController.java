package com.gommit.domain.checkin.controller;

import com.gommit.domain.checkin.service.DailyLogService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// PRIVATE 일일로그 몽타주 영상 서빙. CheckInMediaController 와 같은 패턴 — 도메인별 경로로 각자 서빙한다.
@Tag(name = "DailyLog", description = "일일로그 조회 API")
@RestController
@RequiredArgsConstructor
public class DailyLogMediaController {

    private final DailyLogService dailyLogService;

    @Operation(summary = "일일로그 몽타주 영상 조회")
    @GetMapping("/api/daily-logs/{dailyLogId}/media")
    public ResponseEntity<Resource> get(@CurrentUser SecurityUser actor, @PathVariable Long dailyLogId) {
        Resource resource = dailyLogService.loadMedia(actor.getId(), dailyLogId);
        MediaType contentType =
                MediaTypeFactory.getMediaType(resource.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }
}

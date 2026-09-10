package com.gommit.domain.checkin.controller;

import com.gommit.domain.checkin.service.CheckInService;
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

// PRIVATE 인증 미디어 서빙. 미디어 전달은 도메인별 경로로 각 도메인이 담당한다
// #8(media) 머지 후: 바이트 저장/읽기만 media.StorageService 위임으로 바꾸고, 인가·경로는 유지.
@Tag(name = "CheckIn", description = "인증 제출/조회 API")
@RestController
@RequiredArgsConstructor
public class CheckInMediaController {

    private final CheckInService checkInService;

    @Operation(summary = "인증 미디어 조회")
    @GetMapping("/api/check-ins/{checkInId}/media")
    public ResponseEntity<Resource> get(@CurrentUser SecurityUser actor, @PathVariable Long checkInId) {
        Resource resource = checkInService.loadCheckInMedia(actor.getId(), checkInId);
        MediaType contentType =
                MediaTypeFactory.getMediaType(resource.getFilename()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(contentType).body(resource);
    }
}

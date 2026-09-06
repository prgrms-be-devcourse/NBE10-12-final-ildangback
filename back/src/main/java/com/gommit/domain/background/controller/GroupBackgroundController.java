package com.gommit.domain.background.controller;

import com.gommit.domain.background.dto.request.BackgroundApplyRequest;
import com.gommit.domain.background.dto.response.GroupBackgroundResponse;
import com.gommit.domain.background.service.BackgroundService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Group Background", description = "그룹 배경 조회 및 적용 API")
@RestController
@RequestMapping("/api/groups/{groupId}/background")
@RequiredArgsConstructor
public class GroupBackgroundController {

    private final BackgroundService backgroundService;

    @Operation(summary = "그룹 배경 조회")
    @GetMapping
    public ResponseEntity<GroupBackgroundResponse> getActiveBackground(@PathVariable Long groupId) {
        return ResponseEntity.ok(backgroundService.getActiveBackground(groupId));
    }

    @Operation(summary = "그룹 배경 적용")
    @PatchMapping
    public ResponseEntity<GroupBackgroundResponse> applyBackground(
            @PathVariable Long groupId,
            @CurrentUser SecurityUser actor,
            @Valid @RequestBody BackgroundApplyRequest request) {
        return ResponseEntity.ok(backgroundService.applyBackground(groupId, actor.getId(), request.backgroundId()));
    }
}

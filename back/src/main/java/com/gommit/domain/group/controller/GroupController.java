package com.gommit.domain.group.controller;

import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.dto.response.GroupDetailResponse;
import com.gommit.domain.group.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "group - 그룹")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @Operation(summary = "그룹 생성", description = "그룹과 첫 번째 챌린지 함께 생성")
    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(
        @Valid @RequestBody GroupCreateRequest request
    ) {
        // TODO: 인증 연동 후 로그인 사용자 Id로 변경
        Long userId = 1L;

        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(userId, request));
    }
}

package com.gommit.domain.group.controller;

import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.dto.response.GroupDetailResponse;
import com.gommit.domain.group.dto.response.GroupJoinResponse;
import com.gommit.domain.group.dto.response.GroupSummaryCursorResponse;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupSort;
import com.gommit.domain.group.service.GroupService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "group - 그룹")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @Operation(summary = "그룹 생성", description = "그룹과 첫 번째 챌린지 함께 생성")
    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(
        @CurrentUser SecurityUser actor,
        @Valid @RequestBody GroupCreateRequest request
    ) {
        Long userId = actor.getId();

        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(userId, request));
    }

    @GetMapping
    public ResponseEntity<GroupSummaryCursorResponse> getPublicGroups(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) GroupCategory category,
        @RequestParam(defaultValue = "LATEST") GroupSort sort,
        @RequestParam(required = false) Long cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        GroupSummaryCursorResponse response = groupService.getPublicGroups(keyword, category, sort, cursor, size);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(
        @PathVariable Long groupId
    ) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }

    // 그릅 참여
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupJoinResponse> joinGroup(
        @PathVariable Long groupId,
        @CurrentUser SecurityUser actor
    ) {
        GroupJoinResponse response =  groupService.joinGroup(groupId, actor.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

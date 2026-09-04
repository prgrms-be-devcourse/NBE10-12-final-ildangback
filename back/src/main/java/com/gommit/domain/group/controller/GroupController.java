package com.gommit.domain.group.controller;

import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.dto.response.GroupDetailResponse;
import com.gommit.domain.group.dto.response.GroupJoinResponse;
import com.gommit.domain.group.dto.response.GroupSummaryCursorResponse;
import com.gommit.domain.group.dto.response.MyGroupCursorResponse;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupSort;
import com.gommit.domain.group.entity.GroupStatus;
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

    @Operation(summary = "그룹 생성", description = "새로운 그룹을 생성하고 그룹 생성자를 첫 번째 멤버로 등록. 그룹 생성과 동시에 첫 번째 READY 챌린지가 생성")
    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(
            @CurrentUser SecurityUser actor, @Valid @RequestBody GroupCreateRequest request) {
        Long userId = actor.getId();

        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(userId, request));
    }

    @Operation(summary = "공개 그룹 목록 조회", description = "현재 참여 가능한 공개 그룹 목록을 조회")
    @GetMapping
    public ResponseEntity<GroupSummaryCursorResponse> getPublicGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GroupCategory category,
            @RequestParam(defaultValue = "LATEST") GroupSort sort,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        GroupSummaryCursorResponse response = groupService.getPublicGroups(keyword, category, sort, cursor, size);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 그룹 목록 조회", description = "현재 로그인한 사용자가 참여 중이거나 정상 종료한 그룹 목록을 조회")
    @GetMapping("/me")
    public ResponseEntity<MyGroupCursorResponse> getMyGroups(
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) GroupStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        MyGroupCursorResponse response = groupService.getMyGroups(actor.getId(), status, cursor, size);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "그룹 상세 조회",
            description = "그룹 기본 정보와 현재 참여 중인 멤버, 현재 챌린지 정보를 조회. ACTIVE 챌린지를 우선 조회하며, 없으면 READY 챌린지를 조회")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(@PathVariable Long groupId) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId));
    }

    // 그릅 참여
    @Operation(summary = "공개 그룹 참여", description = "현재 로그인한 사용자가 모집 중인 공개 그룹에 참여. 그룹 멤버와 현재 READY 챌린지의 챌린지 멤버로 함께 등록.")
    @PostMapping("/{groupId}/members")
    public ResponseEntity<GroupJoinResponse> joinGroup(@PathVariable Long groupId, @CurrentUser SecurityUser actor) {
        GroupJoinResponse response = groupService.joinGroup(groupId, actor.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "그룹 퇴장",
            description =
                    "현재 로그인한 사용자가 그룹에서 퇴장. 그룹 멤버와 현재 ACTIVE 또는 READY 챌린지의 멤버 상태가 LEFT로 변경. 그룹 OWNER는 OWNER 위임 전에는 퇴장할 수 없음")
    @DeleteMapping("/{groupId}/members/me")
    public ResponseEntity<Void> leaveGroup(@PathVariable Long groupId, @CurrentUser SecurityUser actor) {
        groupService.leaveGroup(groupId, actor.getId());

        return ResponseEntity.noContent().build();
    }
}

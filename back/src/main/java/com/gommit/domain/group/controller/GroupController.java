package com.gommit.domain.group.controller;

import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.dto.request.GroupJoinRequest;
import com.gommit.domain.group.dto.response.*;
import com.gommit.domain.group.entity.GroupCategory;
import com.gommit.domain.group.entity.GroupSort;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.service.GroupService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "group - 그룹")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {
    private final GroupService groupService;

    @Operation(summary = "그룹 생성", description = "새로운 그룹을 생성하고 그룹 생성자를 첫 번째 멤버로 등록. 그룹 생성과 동시에 첫 번째 READY 챌린지가 생성")
    @PostMapping
    public ResponseEntity<GroupDetailResponse> createGroup(
            @CurrentUser SecurityUser actor, @Valid @RequestBody GroupCreateRequest request) {
        Long userId = actor.getId();

        return ResponseEntity.status(HttpStatus.CREATED).body(groupService.createGroup(userId, request));
    }

    @Operation(summary = "초대코드로 그룹 참여", description = "초대코드를 사용하여 모집 중인 CODE_ONLY 그룹에 참여, 최초 시즌 시작 이후에는 참여 불가")
    @PostMapping("/join")
    public ResponseEntity<GroupJoinResponse> joinGroupByInviteCode(
            @Valid @RequestBody GroupJoinRequest request, @CurrentUser SecurityUser user) {
        GroupJoinResponse response = groupService.joinGroupByInviteCode(request.inviteCode(), user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "그룹 초대코드 조회", description = "그룹장이 초대코드를 조회합니다.")
    @GetMapping("/{groupId}/inviteCode")
    public ResponseEntity<InviteCodeResponse> getInviteCode(
            @PathVariable Long groupId, @CurrentUser SecurityUser user) {
        InviteCodeResponse response = groupService.getInviteCode(groupId, user.getId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "공개 그룹 목록 조회", description = "현재 참여 가능한 공개 그룹 목록을 조회")
    @GetMapping
    public ResponseEntity<SliceResponse<GroupSummaryResponse>> getPublicGroups(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) GroupCategory category,
            @RequestParam(defaultValue = "LATEST") GroupSort sort,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        SliceResponse<GroupSummaryResponse> response =
                groupService.getPublicGroups(keyword, category, sort, cursor, size);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 그룹 목록 조회", description = "현재 로그인한 사용자가 참여 중이거나 정상 종료한 그룹 목록을 조회")
    @GetMapping("/me")
    public ResponseEntity<SliceResponse<MyGroupSummaryResponse>> getMyGroups(
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) GroupStatus status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        SliceResponse<MyGroupSummaryResponse> response = groupService.getMyGroups(actor.getId(), status, cursor, size);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "그룹 상세 조회",
            description = "그룹 기본 정보와 현재 참여 중인 멤버, 현재 챌린지 정보를 조회. ACTIVE 챌린지를 우선 조회하며, 없으면 READY 챌린지를 조회")
    @GetMapping("/{groupId}")
    public ResponseEntity<GroupDetailResponse> getGroupDetail(
            @PathVariable Long groupId, @CurrentUser SecurityUser user) {
        return ResponseEntity.ok(groupService.getGroupDetail(groupId, user.getId()));
    }

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

    @Operation(summary = "그룹원 강퇴", description = "그룹 OWNER가 현재 ACTIVE 챌린지에 참여 중인 그룹원을 강퇴한다.")
    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> kickMember(
            @PathVariable Long groupId, @PathVariable Long userId, @CurrentUser SecurityUser actor) {
        groupService.kickMember(groupId, actor.getId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "그룹의 챌린지(시즌) 목록 조회", description = "해당 그룹의 전체 시즌 목록을 조회")
    @GetMapping("/{groupId}/challenges")
    public ResponseEntity<List<SeasonSummary>> getGroupChallenges(
        @PathVariable Long groupId, @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(groupService.getGroupChallenges(groupId, actor.getId()));
    }
}

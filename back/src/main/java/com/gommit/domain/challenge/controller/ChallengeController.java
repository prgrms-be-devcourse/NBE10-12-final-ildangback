package com.gommit.domain.challenge.controller;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.ExtensionChoiceRequest;
import com.gommit.domain.challenge.dto.request.OwnerDelegationRequest;
import com.gommit.domain.challenge.dto.response.*;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "challenge - 챌린지")
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {
    private final ChallengeService challengeService;

    @Operation(summary = "챌린지 현황 조회", description = "챌린지의 진행 현황과 내 인증 상태를 조회합니다.")
    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeStatusResponse> getChallengeStatus(@PathVariable Long challengeId, @CurrentUser SecurityUser actor) {
        ChallengeStatusResponse response = challengeService.getChallengeStatus(challengeId, actor.getId());

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "챌린지 멤버 오늘 인증 현황 조회", description = "현재 챌린지에 참여 중인 멤버들의 오늘 인증 횟수를 조회합니다.")
    @GetMapping("/{challengeId}/members")
    public ResponseEntity<List<MemberTodayStatusResponse>> getMemberTodayStatuses(@PathVariable Long challengeId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(challengeService.getMemberTodayStatuses(challengeId, actor.getId()));
    }

    @Operation(summary = "챌린지 설정 수정", description = "READY 상태의 챌린지 설정을 OWNER가 수정합니다.")
    @PatchMapping("/{challengeId}")
    public ResponseEntity<ChallengeUpdateResponse> updateChallenge(@PathVariable Long challengeId, @CurrentUser SecurityUser actor, @RequestBody ChallengeUpdateRequest request) {
        ChallengeUpdateResponse response = challengeService.updateChallenge(challengeId, actor.getId(), request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "챌린지 OWNER 위임", description = "현재 챌린지 OWNER가 다른 참여 멤버에게 OWNER 권한을 위임합니다.")
    @PatchMapping("/{challengeId}/owner")
    public ResponseEntity<OwnerDelegationResponse> delegateOwner(@PathVariable Long challengeId, @CurrentUser SecurityUser actor, @RequestBody OwnerDelegationRequest request) {
        OwnerDelegationResponse response = challengeService.delegateOwner(challengeId, actor.getId(), request);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "다음 시즌 연장 참여 의사 선택", description = "현재 챌린지 멤버가 다음 시즌 연장 참여 여부를 선택합니다.")
    @PutMapping("/{challengeId}/extension/choice")
    public ResponseEntity<ExtensionChoiceResponse> updateExtensionChoice(@PathVariable Long challengeId, @CurrentUser SecurityUser actor, @Valid @RequestBody ExtensionChoiceRequest request) {
        ExtensionChoiceResponse response = challengeService.updateExtensionChoice(challengeId, actor.getId(), request);

        return ResponseEntity.ok(response);
    }
}

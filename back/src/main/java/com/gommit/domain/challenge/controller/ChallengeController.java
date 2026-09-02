package com.gommit.domain.challenge.controller;

import com.gommit.domain.challenge.dto.request.ChallengeUpdateRequest;
import com.gommit.domain.challenge.dto.request.OwnerDelegationRequest;
import com.gommit.domain.challenge.dto.response.ChallengeStatusResponse;
import com.gommit.domain.challenge.dto.response.ChallengeUpdateResponse;
import com.gommit.domain.challenge.dto.response.MemberTodayStatusResponse;
import com.gommit.domain.challenge.dto.response.OwnerDelegationResponse;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeStatusResponse> getChallengeStatus(@PathVariable Long challengeId, @CurrentUser SecurityUser actor) {
        ChallengeStatusResponse response = challengeService.getChallengeStatus(challengeId, actor.getId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{challengeId}/members")
    public ResponseEntity<List<MemberTodayStatusResponse>> getMemberTodayStatuses(@PathVariable Long challengeId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(challengeService.getMemberTodayStatuses(challengeId, actor.getId()));
    }

    @PatchMapping("/{challengeId}")
    public ResponseEntity<ChallengeUpdateResponse> updateChallenge(@PathVariable Long challengeId, @CurrentUser SecurityUser actor, @RequestBody ChallengeUpdateRequest request) {
        ChallengeUpdateResponse response = challengeService.updateChallenge(challengeId, actor.getId(), request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{challengeId}/owner")
    public ResponseEntity<OwnerDelegationResponse> delegateOwner(@PathVariable Long challengeId, @CurrentUser SecurityUser actor, @RequestBody OwnerDelegationRequest request) {
        OwnerDelegationResponse response = challengeService.delegateOwner(challengeId, actor.getId(), request);

        return ResponseEntity.ok(response);
    }
}

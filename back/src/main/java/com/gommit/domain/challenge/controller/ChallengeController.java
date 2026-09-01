package com.gommit.domain.challenge.controller;

import com.gommit.domain.challenge.dto.response.ChallengeStatusResponse;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "challenge - 챌린지")
@RestController
@RequestMapping("/api/challenges")
@RequiredArgsConstructor
public class ChallengeController {
    private final ChallengeService challengeService;

    @GetMapping("/{challengeId}")
    public ResponseEntity<ChallengeStatusResponse> getChallengeStatus(
        @PathVariable Long challengeId,
        @CurrentUser SecurityUser actor
    ) {
        ChallengeStatusResponse response =
            challengeService.getChallengeStatus(
                challengeId,
                actor.getId()
            );

        return ResponseEntity.ok(response);
    }
}

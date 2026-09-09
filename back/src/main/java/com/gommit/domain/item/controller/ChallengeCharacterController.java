package com.gommit.domain.item.controller;

import com.gommit.domain.item.dto.response.ChallengeCharacterResponse;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "challenge - 챌린지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/challenges")
public class ChallengeCharacterController {
    private final UserItemService userItemService;

    @GetMapping("/{challengeId}/characters")
    @Operation(summary = "챌린지 멤버 캐릭터 조회")
    public ResponseEntity<List<ChallengeCharacterResponse>> getChallengeCharacters(
            @PathVariable Long challengeId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userItemService.getChallengeCharacters(challengeId, actor.getId()));
    }
}

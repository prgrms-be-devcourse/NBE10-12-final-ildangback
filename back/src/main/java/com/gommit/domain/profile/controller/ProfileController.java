package com.gommit.domain.profile.controller;

import com.gommit.domain.profile.dto.response.ProfileResponse;
import com.gommit.domain.profile.service.ProfileService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile", description = "프로필 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ProfileController {
    private final ProfileService profileService;

    // 프로필 메인 조회
    @GetMapping("/users/me/profile")
    @Operation(summary = "프로필 조회")
    public ResponseEntity<ProfileResponse> getMyProfile(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(profileService.getMyProfile(actor.getId()));
    }
}

package com.gommit.domain.user.controller;

import com.gommit.domain.user.dto.request.ChangePasswordRequest;
import com.gommit.domain.user.dto.request.DeleteAccountRequest;
import com.gommit.domain.user.dto.request.UpdateProfileRequest;
import com.gommit.domain.user.dto.response.UserProfileResponse;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Users", description = "사용자 및 계정 관리 API")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userService.getMyProfile(actor.getId()));
    }

    @Operation(summary = "내 정보 수정")
    @PatchMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @CurrentUser SecurityUser actor, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateMyProfile(actor.getId(), request));
    }

    @Operation(summary = "비밀번호 변경")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @CurrentUser SecurityUser actor, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(actor.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 탈퇴")
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @CurrentUser SecurityUser actor, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(actor.getId(), request);
        return ResponseEntity.noContent().build();
    }
}

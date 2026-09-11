package com.gommit.domain.home.controller;

import com.gommit.domain.home.dto.response.ActivityResponse;
import com.gommit.domain.home.dto.response.GrassResponse;
import com.gommit.domain.home.dto.response.HomeResponse;
import com.gommit.domain.home.service.HomeService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Home", description = "홈 화면 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class HomeController {
    private final HomeService homeService;

    // 홈 화면 조회
    @GetMapping("/home")
    @Operation(summary = "홈 화면 조회")
    public ResponseEntity<HomeResponse> getHome(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(homeService.getHome(actor.getId()));
    }

    // 꼬밋 잔디 조회
    // from에서 to 기간의 날짜별 인증 횟수/레벨을 배열로 반환
    @GetMapping("/users/me/grass")
    @Operation(summary = "꼬밋 잔디 조회")
    public ResponseEntity<SliceResponse<GrassResponse>> getGrass(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(homeService.getGrass(actor.getId(), from, to));
    }

    // 최근 활동 조회
    @GetMapping("/users/me/activities")
    @Operation(summary = "최근 활동 조회")
    public ResponseEntity<SliceResponse<ActivityResponse>> getActivities(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(homeService.getActivities(actor.getId()));
    }
}

package com.gommit.domain.background.controller;

import com.gommit.domain.background.dto.request.BackgroundCreateRequest;
import com.gommit.domain.background.dto.response.BackgroundResponse;
import com.gommit.domain.background.service.BackgroundService;
import com.gommit.global.dto.SliceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Group Background", description = "그룹 배경 조회, 적용과 판매 배경 등록, 삭제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/backgrounds")
@Validated
public class BackgroundAdminController {

    private final BackgroundService backgroundService;

    @Operation(summary = "판매 배경 목록 조회(관리자)")
    @GetMapping
    public ResponseEntity<SliceResponse<BackgroundResponse>> getBackgrounds(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(backgroundService.getBackgrounds(cursor, size));
    }

    @Operation(summary = "판매 배경 등록(관리자)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BackgroundResponse> createBackground(@Valid @ModelAttribute BackgroundCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(backgroundService.createBackground(request));
    }

    @Operation(summary = "판매 배경 삭제(관리자)")
    @DeleteMapping("/{backgroundId}")
    public ResponseEntity<Void> deleteBackground(@PathVariable Long backgroundId) {
        backgroundService.deleteBackground(backgroundId);
        return ResponseEntity.noContent().build();
    }
}

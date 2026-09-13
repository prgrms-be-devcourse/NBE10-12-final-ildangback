package com.gommit.domain.background.controller;

import com.gommit.domain.background.dto.request.PurchaseRequestCreateRequest;
import com.gommit.domain.background.dto.request.PurchaseVoteRequest;
import com.gommit.domain.background.dto.response.PurchaseRequestResponse;
import com.gommit.domain.background.dto.response.ShopBackgroundResponse;
import com.gommit.domain.background.service.BackgroundPurchaseService;
import com.gommit.domain.background.service.BackgroundService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Group Shop", description = "그룹 배경 상점 및 구매 투표 API")
@RestController
@RequestMapping("/api/groups/{groupId}/shop")
@RequiredArgsConstructor
@Validated
public class GroupShopController {

    private final BackgroundService backgroundService;
    private final BackgroundPurchaseService backgroundPurchaseService;

    @Operation(summary = "그룹 상점 조회")
    @GetMapping("/backgrounds")
    public ResponseEntity<SliceResponse<ShopBackgroundResponse>> getShopBackgrounds(
            @PathVariable Long groupId,
            @CurrentUser SecurityUser actor,
            @RequestParam(required = false) Boolean owned,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(backgroundService.getShopBackgrounds(groupId, actor.getId(), owned, cursor, size));
    }

    @Operation(summary = "배경 구매 제안")
    @PostMapping("/purchase-requests")
    public ResponseEntity<PurchaseRequestResponse> createPurchaseRequest(
            @PathVariable Long groupId,
            @CurrentUser SecurityUser actor,
            @Valid @RequestBody PurchaseRequestCreateRequest request) {
        PurchaseRequestResponse response =
                backgroundPurchaseService.createRequest(groupId, actor.getId(), request.backgroundId());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "진행 중인 구매 제안 조회")
    @GetMapping("/purchase-requests/current")
    public ResponseEntity<PurchaseRequestResponse> getCurrentPurchaseRequest(
            @PathVariable Long groupId, @CurrentUser SecurityUser actor) {
        return backgroundPurchaseService
                .getCurrentRequest(groupId, actor.getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "구매 제안 투표")
    @PostMapping("/purchase-requests/{requestId}/votes")
    public ResponseEntity<PurchaseRequestResponse> vote(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @CurrentUser SecurityUser actor,
            @Valid @RequestBody PurchaseVoteRequest request) {
        PurchaseRequestResponse response =
                backgroundPurchaseService.vote(groupId, actor.getId(), requestId, request.agreed());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "구매 제안 취소")
    @DeleteMapping("/purchase-requests/{requestId}")
    public ResponseEntity<Void> cancelPurchaseRequest(
            @PathVariable Long groupId, @PathVariable Long requestId, @CurrentUser SecurityUser actor) {
        backgroundPurchaseService.cancelRequest(groupId, actor.getId(), requestId);

        return ResponseEntity.noContent().build();
    }
}

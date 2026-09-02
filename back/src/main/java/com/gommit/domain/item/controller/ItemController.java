package com.gommit.domain.item.controller;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemListResponse;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.service.ItemService;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
public class ItemController {
    private final ItemService itemService;

    // 상점 아이템 목록 조회
    @GetMapping("/api/items")
    @Operation(summary = "상점 아이템 목록 조회")
    public ResponseEntity<ShopItemListResponse> getShopItems(
            @RequestParam(required = false) ItemSlot slot,
            @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(itemService.getShopItems(actor.getId(), slot));
    }

    // 아이템 구매
    @PostMapping("/api/items/{itemId}/purchase")
    @Operation(summary = "아이템 구매")
    public ResponseEntity<ItemPurchaseResponse> purchase(
            @PathVariable Long itemId,
            @CurrentUser SecurityUser actor
    ) {
        ItemPurchaseResponse response = itemService.purchaseItem(actor.getId(), itemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 관리자 아이템 등록
    @PostMapping(value = "/api/admin/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "아이템 등록(관리자)")
    public ResponseEntity<ItemResponse> createItem(
        @Valid @ModelAttribute ItemCreateRequest request
        ) {
        ItemResponse response = itemService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 관리자 아이템 삭제
    @DeleteMapping("/api/admin/items/{itemId}")
    @Operation(summary = "아이템 삭제(관리자)")
    public ResponseEntity<Void> deleteItem(
        @PathVariable Long itemId
    ) {
        itemService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}

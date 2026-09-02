package com.gommit.domain.item.controller;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemResponse;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.service.ItemService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Item", description = "상점 아이템 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ItemController {
    private final ItemService itemService;

    // 상점 아이템 목록 조회
    // [변경] 반환 타입: ShopItemListResponse → SliceResponse<ShopItemResponse>
    // [변경] 파라미터 추가:
    //   cursor - 마지막으로 받은 itemId. 첫 요청 시 생략(null).
    //   size   - 한 번에 받을 개수. 기본 20. @Min(1)로 0 이하 입력을 컨트롤러 레벨에서 차단.
    @GetMapping("/items")
    @Operation(summary = "상점 아이템 목록 조회")
    public ResponseEntity<SliceResponse<ShopItemResponse>> getShopItems(
            @RequestParam(required = false) ItemSlot slot,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(itemService.getShopItems(actor.getId(), slot, cursor, size));
    }

    // 아이템 구매
    @PostMapping("/items/{itemId}/purchase")
    @Operation(summary = "아이템 구매")
    public ResponseEntity<ItemPurchaseResponse> purchase(@PathVariable Long itemId, @CurrentUser SecurityUser actor) {
        ItemPurchaseResponse response = itemService.purchaseItem(actor.getId(), itemId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 관리자 아이템 등록
    @PostMapping(value = "/admin/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "아이템 등록(관리자)")
    public ResponseEntity<ItemResponse> createItem(@Valid @ModelAttribute ItemCreateRequest request) {
        ItemResponse response = itemService.createItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 관리자 아이템 삭제
    @DeleteMapping("/admin/items/{itemId}")
    @Operation(summary = "아이템 삭제(관리자)")
    public ResponseEntity<Void> deleteItem(@PathVariable Long itemId) {
        itemService.deleteItem(itemId);
        return ResponseEntity.noContent().build();
    }
}

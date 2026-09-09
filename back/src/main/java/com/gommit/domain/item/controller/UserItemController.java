package com.gommit.domain.item.controller;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.security.CurrentUser;
import com.gommit.global.security.SecurityUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "UserItem", description = "보유 아이템/캐릭터 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Validated
public class UserItemController {
    private final UserItemService userItemService;

    // 내 캐릭터 조회
    @GetMapping("/me/character")
    @Operation(summary = "내 캐릭터 조회")
    public ResponseEntity<CharacterResponse> getMyCharacter(@CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userItemService.getMyCharacter(actor.getId()));
    }

    // 여러 유저 캐릭터 조회
    @GetMapping("/characters")
    @Operation(summary = "여러 유저 캐릭터 조회")
    public ResponseEntity<Map<Long, Map<ItemSlot, String>>> getCharacters(
            @RequestParam @NotEmpty @Size(max = 30) List<Long> userIds) {
        return ResponseEntity.ok(userItemService.getCharacters(userIds));
    }

    // 보유 아이템 조회
    @GetMapping("/me/items")
    @Operation(summary = "보유 아이템 조회")
    public ResponseEntity<SliceResponse<UserItemResponse>> getMyItems(
            @RequestParam(required = false) ItemSlot slot,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userItemService.getMyItems(actor.getId(), slot, cursor, size));
    }

    // 아이템 착용
    @PutMapping("/me/items/{userItemId}/equip")
    @Operation(summary = "아이템 착용")
    public ResponseEntity<UserItemResponse> equipItem(@PathVariable Long userItemId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userItemService.equipItem(actor.getId(), userItemId));
    }

    // 아이템 착용 해제
    @DeleteMapping("/me/items/{userItemId}/equip")
    @Operation(summary = "아이템 착용 해제")
    public ResponseEntity<UserItemResponse> unequipItem(
            @PathVariable Long userItemId, @CurrentUser SecurityUser actor) {
        return ResponseEntity.ok(userItemService.unequipItem(actor.getId(), userItemId));
    }
}

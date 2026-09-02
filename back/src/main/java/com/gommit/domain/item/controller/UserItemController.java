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
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Tag(name = "UserItem", description = "보유 아이템/캐릭터 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
public class UserItemController {
    private final UserItemService userItemService;

    /**
     * 내 캐릭터 조회
     * - 로그인 유저의 착용 아이템 4슬롯 + 인증 상태를 조합한 응답을 그대로 감싸서 반환.
     * - 파라미터가 로그인 유저 정보(actor)뿐이라 4개 API 중 가장 단순한 형태.
     */
    @GetMapping("/character")
    @Operation(summary = "내 캐릭터 조회")
    public ResponseEntity<CharacterResponse> getMyCharacter(
        @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(userItemService.getMyCharacter(actor.getId()));
    }

    /**
     * 보유 아이템 조회
     * - slot 쿼리 파라미터가 있으면 해당 슬롯만, 없으면 전체 보유 아이템 반환.
     * [변경] 반환 타입: List<UserItemResponse> → SliceResponse<UserItemResponse>
     *   - 기존 배열 응답은 hasNext, nextCursor를 추가하려면 구조 자체를 바꿔야 해서
     *     프론트 코드까지 영향. SliceResponse로 통일하면 이후 변경에 유연하게 대응 가능.
     * [변경] 파라미터 추가:
     *   cursor - 마지막으로 받은 userItemId. 첫 요청 시 생략(null).
     *   size   - 한 번에 받을 개수. 기본 20. @Min(1)로 0 이하 입력을 컨트롤러 레벨에서 차단.
     */
    @GetMapping("/items")
    @Operation(summary = "보유 아이템 조회")
    public ResponseEntity<SliceResponse<UserItemResponse>> getMyItems(
        @RequestParam(required = false) ItemSlot slot,
        @RequestParam(required = false) Long cursor,
        @RequestParam(defaultValue = "20") @Min(1) int size,
        @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(userItemService.getMyItems(actor.getId(), slot, cursor, size));
    }

    /**
     * 아이템 착용
     * - 경로의 {userItemId}는 "아이템 자체의 id"가 아니라 "내가 보유한 그 아이템 레코드의 id".
     * - 소유자 검증/이미 착용 여부 체크는 Service 내부(equipItem)에서 처리하므로
     *   Controller는 값 전달만 담당.
     * - 착용 성공은 명세서상 200이라 별도 상태코드 지정 없이 .ok()
     */
    @PutMapping("/items/{userItemId}/equip")
    @Operation(summary = "아이템 착용")
    public ResponseEntity<UserItemResponse> equipItem(
        @PathVariable Long userItemId,
        @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(userItemService.equipItem(actor.getId(), userItemId));
    }

    /**
     * 아이템 착용 해제
     * - HTTP 메서드는 DELETE이지만, 명세서 응답이 200 + UserItemResponse 본문이 있는 케이스.
     *   (참고: 관리자 아이템 삭제(DELETE /admin/items/{itemId})는 204 No Content였는데,
     *    이건 리소스 자체를 지우는 게 아니라 "착용 상태만 해제"하는 것이라 응답 형태가 다름)
     */
    @DeleteMapping("/items/{userItemId}/equip")
    @Operation(summary = "아이템 착용 해제")
    public ResponseEntity<UserItemResponse> unequipItem(
        @PathVariable Long userItemId,
        @CurrentUser SecurityUser actor
    ) {
        return ResponseEntity.ok(userItemService.unequipItem(actor.getId(), userItemId));
    }

}

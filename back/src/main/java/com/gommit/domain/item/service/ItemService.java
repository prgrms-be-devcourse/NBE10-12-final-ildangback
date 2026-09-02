package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.ItemRepository;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final UserItemService userItemService;

    // 이미지 임시 경로
    private String uploadImage(MultipartFile image) {
        // 임시 고정 경로
        return "https://cdn.phototourl.com/free/2026-09-02-404c3e23-3aa1-46f2-b0e2-4e2c239530ce.jpg" + image.getOriginalFilename();
    }

    // 상점 아이템 목록 조회
    // [변경] 반환 타입: ShopItemListResponse → SliceResponse<ShopItemResponse>
    // [변경] 파라미터: cursor, size 추가
    // - cursor: 마지막으로 받은 itemId. null이면 첫 요청(처음부터 조회).
    // - size: 한 번에 가져올 아이템 수.
    public SliceResponse<ShopItemResponse> getShopItems(Long userId, ItemSlot slot, Long cursor, int size) {
        // cursor가 null(첫 요청)이면 0으로 처리 → WHERE id > 0 = 전체 범위
        long effectiveCursor = cursor != null ? cursor : 0L;

        // size+1개를 요청해 다음 페이지 존재 여부를 판단한다.
        // ofCursor 내부에서 rows.size() > size이면 hasNext=true로 처리하고 마지막 1개를 버림.
        Pageable pageable = PageRequest.of(0, size + 1);

        List<Item> items;
        if(slot == null) {
            items = itemRepository.findByIdGreaterThanOrderByIdAsc(effectiveCursor, pageable);
        } else {
            items = itemRepository.findBySlotAndIdGreaterThanOrderByIdAsc(slot, effectiveCursor, pageable);
        }

        // 보유 아이템은 커서 없이 전체를 가져온다.
        // 상점 커서가 Item 기준이므로 UserItem 전체를 Map으로 만들어 O(1)로 보유 여부를 체크함.
        List<UserItem> userItems = userItemRepository.findByUserId(userId);
        Map<Long, UserItem> ownedMap = new HashMap<>();
        for(UserItem userItem : userItems) {
            ownedMap.put(userItem.getItem().getId(), userItem);
        }

        List<ShopItemResponse> responseList = new ArrayList<>();
        for(Item item : items) {
            UserItem matchedUserItem = ownedMap.get(item.getId());
            boolean owned = matchedUserItem != null;
            boolean equipped = matchedUserItem != null && matchedUserItem.isEquipped();

            ItemResponse itemResponse = new ItemResponse(item);
            responseList.add(new ShopItemResponse(itemResponse, owned, equipped));
        }

        // nextCursor는 마지막 항목의 itemId.
        // 다음 요청 시 ?cursor={nextCursor}로 넘기면 그 이후부터 이어서 가져옴.
        return SliceResponse.ofCursor(responseList, size, r -> r.item().id());
    }

    // 아이템 구매
    @Transactional
    public ItemPurchaseResponse purchaseItem(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if(userItemRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED_ITEM);
        }

        // 포인트 차감 메서드 호출
        // pointService.deduct(userId, item.getPrice());

        UserItem newUserItem = UserItem.of(userId, item);
        UserItem savedUserItem = userItemRepository.save(newUserItem);

        userItemService.switchEquippedItem(userId, savedUserItem);

        // 차감 후 잔액 받아오기
        int remainingBalance = 0;
        return new ItemPurchaseResponse(
            savedUserItem.getId(),
            item.getId(),
            savedUserItem.getCreatedAt(),
            remainingBalance, // 차감 후 잔액
            savedUserItem.getEquippedSlot()
        );
    }

    // 아이템 등록 (관리자)
    @Transactional
    public ItemResponse createItem(ItemCreateRequest request) {
        String imageUrl = uploadImage(request.image());
        Item newItem = Item.of(request.slot(), request.name(), imageUrl, request.price());
        Item savedItem = itemRepository.save(newItem);
        return new ItemResponse(savedItem);
    }

    // 아이템 삭제 (관리자)
    @Transactional
    public void deleteItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if(userItemRepository.existsByItemId(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE);
        }
        itemRepository.deleteById(itemId);
    }

}

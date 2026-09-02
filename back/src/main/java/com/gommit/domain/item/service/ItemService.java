package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemListResponse;
import com.gommit.domain.item.dto.response.ShopItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.ItemRepository;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
        return "https://example.com/images/temp-" + image.getOriginalFilename();
    }

    // 상점 아이템 목록 조회
    public ShopItemListResponse getShopItems(Long userId, ItemSlot slot) {
        List<Item> items;
        if(slot == null) {
            items = itemRepository.findAll();
        } else {
            items = itemRepository.findBySlot(slot);
        }

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
            ShopItemResponse shopItemResponse = new ShopItemResponse(itemResponse, owned, equipped);
            responseList.add(shopItemResponse);
        }

        return new ShopItemListResponse(responseList);
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
        String imageUrl = uploadImage(request.getImage());
        Item newItem = Item.of(request.getSlot(), request.getName(), imageUrl, request.getPrice());
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

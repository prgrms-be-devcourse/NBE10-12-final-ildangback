package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemResponse;
import com.gommit.domain.item.entity.*;
import com.gommit.domain.item.repository.ItemImageRepository;
import com.gommit.domain.item.repository.ItemRepository;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaValidator;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final UserItemRepository userItemRepository;
    private final UserItemService userItemService;
    private final PointService pointService;
    private final StorageService storageService;
    private final MediaValidator mediaValidator;
    private final ItemWriter itemWriter;

    // 상점 아이템 목록 조회
    public SliceResponse<ShopItemResponse> getShopItems(Long userId, ItemSlot slot, Long cursor, int size) {
        long effectiveCursor = cursor != null ? cursor : 0L;
        Pageable pageable = PageRequest.of(0, size + 1);

        List<Item> items;
        if (slot == null) {
            items = itemRepository.findByIdGreaterThanOrderByIdAsc(effectiveCursor, pageable);
        } else {
            items = itemRepository.findBySlotAndIdGreaterThanOrderByIdAsc(slot, effectiveCursor, pageable);
        }

        List<Long> itemIds = items.stream().map(Item::getId).toList();
        Map<Long, String> defaultImageUrls = loadDefaultImageUrls(itemIds);

        List<UserItem> userItems = userItemRepository.findByUserId(userId);
        Map<Long, UserItem> ownedMap = new HashMap<>();
        for (UserItem ui : userItems) {
            ownedMap.put(ui.getItem().getId(), ui);
        }

        List<ShopItemResponse> responseList = new ArrayList<>();
        for (Item item : items) {
            UserItem matchedUserItem = ownedMap.get(item.getId());
            boolean owned = matchedUserItem != null;
            boolean equipped = matchedUserItem != null && matchedUserItem.isEquipped();

            String imageUrl = defaultImageUrls.get(item.getId());
            responseList.add(new ShopItemResponse(new ItemResponse(item, imageUrl), owned, equipped));
        }

        return SliceResponse.ofCursor(responseList, size, r -> r.item().id());
    }

    // 아이템 구매
    @Transactional
    public ItemPurchaseResponse purchaseItem(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (userItemRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED_ITEM);
        }

        // 포인트 차감 메서드 호출
        pointService.deduct(userId, item.getPrice(), UserPointReason.ITEM_PURCHASE, item.getName());

        UserItem newUserItem = UserItem.of(userId, item);
        UserItem savedUserItem;
        try {
            savedUserItem = userItemRepository.saveAndFlush(newUserItem);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.ALREADY_OWNED_ITEM);
        }

        userItemService.switchEquippedItem(userId, savedUserItem);

        // 차감 후 잔액 받아오기
        PointBalanceResponse balanceResponse = pointService.getMyBalance(userId);

        return new ItemPurchaseResponse(
                savedUserItem.getId(),
                item.getId(),
                savedUserItem.getCreatedAt(),
                balanceResponse.balance(),
                savedUserItem.getEquippedSlot());
    }

    // 아이템 등록 (관리자)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ItemResponse createItem(ItemCreateRequest request) {
        validateCreateRequest(request);

        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (MultipartFile file : request.images()) {
                mediaValidator.validate(file, MediaRole.ITEM);
                StorageResult result = storageService.store(file, MediaRole.ITEM);
                uploadedKeys.add(result.storageKey());
            }
            return itemWriter.saveItemWithImages(request, uploadedKeys);
        } catch (Exception e) {
            for (String key : uploadedKeys) {
                storageService.delete(key, MediaRole.ITEM);
            }
            throw e;
        }
    }

    // 아이템 삭제 (관리자)
    @Transactional
    public void deleteItem(Long itemId) {
        Item item = itemRepository.findById(itemId).orElseThrow(() -> new BusinessException(ErrorCode.ITEM_NOT_FOUND));
        if (userItemRepository.existsByItemId(itemId)) {
            throw new BusinessException(ErrorCode.ITEM_IN_USE);
        }

        List<ItemImage> images = itemImageRepository.findByItemId(itemId);
        itemRepository.delete(item);
        itemRepository.flush();

        for (ItemImage img : images) {
            try {
                storageService.delete(img.getImageKey(), MediaRole.ITEM);
            } catch (Exception e) {
                log.warn("S3 파일 삭제 실패: {}", img.getImageKey(), e);
            }

        }
    }

    Map<Long, String> loadDefaultImageUrls(List<Long> itemIds) {
        if (itemIds.isEmpty()) return Collections.emptyMap();
        List<ItemImage> images = itemImageRepository.findByItemIdInAndPose(itemIds, Pose.DEFAULT);
        Map<Long, String> result = new HashMap<>();
        for (ItemImage img : images) {
            result.put(img.getItem().getId(), storageService.publicUrl(img.getImageKey()));
        }
        return result;
    }

    private void validateCreateRequest(ItemCreateRequest request) {
        if (request.poses().size() != request.images().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!request.poses().contains(Pose.DEFAULT)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (new HashSet<>(request.poses()).size() != request.poses().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}

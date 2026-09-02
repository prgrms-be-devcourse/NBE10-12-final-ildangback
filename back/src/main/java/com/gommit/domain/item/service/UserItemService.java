package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.CheckInState;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemService {
    private final UserItemRepository userItemRepository;

    // 아이템 착용
    @Transactional
    public UserItemResponse equipItem(Long userId, Long userItemId) {
        UserItem targetUserItem = userItemRepository.findById(userItemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND));
        if(!targetUserItem.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_OWNER);
        }
        if(targetUserItem.isEquipped()) {
            throw new BusinessException(ErrorCode.ALREADY_EQUIPPED);
        }

        switchEquippedItem(userId, targetUserItem);

        ItemResponse itemResponse = new ItemResponse(targetUserItem.getItem());
        return new UserItemResponse(targetUserItem, itemResponse);
    }

    void switchEquippedItem(Long userId, UserItem targetUserItem) {
        ItemSlot slot = targetUserItem.getItem().getSlot();
        userItemRepository.findByUserIdAndEquippedSlot(userId, slot)
            .ifPresent(UserItem::unequip);
        targetUserItem.equip();
    }

    // 아이템 착용 해제
    @Transactional
    public UserItemResponse unequipItem(Long userId, Long userItemId) {
        UserItem targetUserItem =  userItemRepository.findById(userItemId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND));
        if(!targetUserItem.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_OWNER);
        }
        if(!targetUserItem.isEquipped()) {
            throw new BusinessException(ErrorCode.NOT_EQUIPPED);
        }

        targetUserItem.unequip();

        ItemResponse itemResponse = new ItemResponse(targetUserItem.getItem());
        return new UserItemResponse(targetUserItem, itemResponse);
    }

    // 보유 아이템 조회
    public List<UserItemResponse> getMyItems(Long userId, ItemSlot slot) {
        List<UserItem> userItems;
        if(slot == null) {
            userItems = userItemRepository.findByUserId(userId);
        } else {
            userItems = userItemRepository.findByUserIdAndItem_Slot(userId, slot);
        }

        List<UserItemResponse> responseList = new ArrayList<>();
        for(UserItem userItem : userItems) {
            ItemResponse itemResponse = new ItemResponse(userItem.getItem());
            UserItemResponse userItemResponse = new UserItemResponse(userItem, itemResponse);
            responseList.add(userItemResponse);
        }
        return responseList;
    }

    // 내 캐릭터 조회
    public CharacterResponse getMyCharacter(Long userId) {
        List<UserItem> equippedItems = userItemRepository.findByUserIdAndEquippedSlotNotNull(userId);

        Map<ItemSlot, String> slotMap = new HashMap<>();
        for(ItemSlot slot : ItemSlot.values()) {
            slotMap.put(slot, null);
        }
        for(UserItem userItem : equippedItems) {
            slotMap.put(userItem.getEquippedSlot(), userItem.getItem().getImageUrl());
        }

        // checkInState - 임시 값
        CheckInState checkInState = CheckInState.NOT_DONE;

        return new CharacterResponse(slotMap, checkInState);
    }
}

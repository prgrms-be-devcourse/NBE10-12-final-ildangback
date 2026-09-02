package com.gommit.domain.item.service;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.CheckInState;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    // [변경] 반환 타입: List<UserItemResponse> → SliceResponse<UserItemResponse>
    // [변경] 파라미터: cursor, size 추가
    // - cursor: 마지막으로 받은 userItemId. null이면 첫 요청.
    // - size: 한 번에 가져올 아이템 수.
    public SliceResponse<UserItemResponse> getMyItems(Long userId, ItemSlot slot, Long cursor, int size) {
        // cursor가 null(첫 요청)이면 0으로 처리 → WHERE id > 0 = 전체 범위
        long effectiveCursor = cursor != null ? cursor : 0L;

        // size+1개를 요청해 다음 페이지 존재 여부를 판단한다.
        Pageable pageable = PageRequest.of(0, size + 1);

        List<UserItem> userItems;
        if(slot == null) {
            // 슬롯 미지정: 보유 아이템 전체를 커서 기반으로 조회
            userItems = userItemRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(userId, effectiveCursor, pageable);
        } else {
            // 슬롯 지정: 해당 슬롯 아이템만 커서 기반으로 조회
            userItems = userItemRepository.findByUserIdAndItem_SlotAndIdGreaterThanOrderByIdAsc(userId, slot, effectiveCursor, pageable);
        }

        List<UserItemResponse> responseList = new ArrayList<>();
        for(UserItem userItem : userItems) {
            ItemResponse itemResponse = new ItemResponse(userItem.getItem());
            responseList.add(new UserItemResponse(userItem, itemResponse));
        }

        // nextCursor는 마지막 항목의 userItemId.
        // 다음 요청 시 ?cursor={nextCursor}로 넘기면 그 이후부터 이어서 가져옴.
        return SliceResponse.ofCursor(responseList, size, UserItemResponse::id);
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

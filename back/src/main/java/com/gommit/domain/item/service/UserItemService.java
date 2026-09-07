package com.gommit.domain.item.service;

import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.*;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserItemService {
    private final UserItemRepository userItemRepository;
    private final CheckInRepository checkInRepository;
    private final StorageService storageService;

    // 아이템 착용
    @Transactional
    public UserItemResponse equipItem(Long userId, Long userItemId) {
        UserItem targetUserItem = userItemRepository
                .findById(userItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND));
        if (!targetUserItem.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_OWNER);
        }
        if (targetUserItem.isEquipped()) {
            throw new BusinessException(ErrorCode.ALREADY_EQUIPPED);
        }

        switchEquippedItem(userId, targetUserItem);

        return toUserItemResponse(targetUserItem, Pose.DEFAULT);
    }

    void switchEquippedItem(Long userId, UserItem targetUserItem) {
        ItemSlot slot = targetUserItem.getItem().getSlot();
        userItemRepository.findByUserIdAndEquippedSlot(userId, slot).ifPresent(existing -> {
            existing.unequip();
            userItemRepository.flush(); // unequip UPDATE를 db에 반영
        });
        targetUserItem.equip(); // 이후 equip
    }

    // 아이템 착용 해제
    @Transactional
    public UserItemResponse unequipItem(Long userId, Long userItemId) {
        UserItem targetUserItem = userItemRepository
                .findById(userItemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_ITEM_NOT_FOUND));
        if (!targetUserItem.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_ITEM_OWNER);
        }
        if (!targetUserItem.isEquipped()) {
            throw new BusinessException(ErrorCode.NOT_EQUIPPED);
        }

        targetUserItem.unequip();

        return toUserItemResponse(targetUserItem, Pose.DEFAULT);
    }

    // 보유 아이템 조회
    public SliceResponse<UserItemResponse> getMyItems(Long userId, ItemSlot slot, Long cursor, int size) {
        long effectiveCursor = cursor != null ? cursor : 0L;
        Pageable pageable = PageRequest.of(0, size + 1);

        List<UserItem> userItems;
        if (slot == null) {
            userItems = userItemRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(userId, effectiveCursor, pageable);
        } else {
            userItems = userItemRepository.findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
                    userId, slot, effectiveCursor, pageable);
        }

        List<UserItemResponse> responseList = new ArrayList<>();
        for (UserItem userItem : userItems) {
            responseList.add(toUserItemResponse(userItem, Pose.DEFAULT));
        }

        return SliceResponse.ofCursor(responseList, size, UserItemResponse::id);
    }

    // 내 캐릭터 조회
    public CharacterResponse getMyCharacter(Long userId) {
        LocalDate today = LocalDateTime.now().minusHours(4).toLocalDate();
        boolean checkedIn = checkInRepository.existsByUserIdAndBusinessDate(userId, today);
        CheckInState checkInState = checkedIn ? CheckInState.DONE : CheckInState.NOT_DONE;

        // TODO: 챌린지 도메인 완성 후 교체
        // 참여 중인 챌린지 목록 + 종류 조회 필요
        // 성공 2/3 이상 -> 성공 포즈 / 실패 2/3 이상 -> 실패 포즈 / 그 외 기본 포즈
        // 종류가 섞였을 때 어느 포즈를 쓸 지 규칙 미정
        Pose pose = (checkInState == CheckInState.DONE) ? Pose.WEIGHT_FAIL : Pose.DEFAULT;

        List<UserItem> equippedItems = userItemRepository.findByUserIdAndEquippedSlotNotNull(userId);

        Map<ItemSlot, String> slotMap = new HashMap<>();
        for (ItemSlot slot : ItemSlot.values()) {
            slotMap.put(slot, null);
        }
        for (UserItem userItem : equippedItems) {
            String key = userItem.getItem().imageKeyForPose(pose);
            String url = key != null ? storageService.publicUrl(key) : null;
            slotMap.put(userItem.getEquippedSlot(), url);
        }

        return new CharacterResponse(slotMap, checkInState);
    }

    private UserItemResponse toUserItemResponse(UserItem userItem, Pose pose) {
        Item item = userItem.getItem();
        String key = userItem.getItem().imageKeyForPose(pose);
        String url = key != null ? storageService.publicUrl(key) : null;
        return new UserItemResponse(userItem, new ItemResponse(item, url));
    }
}

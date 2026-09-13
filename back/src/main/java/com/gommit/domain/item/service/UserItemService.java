package com.gommit.domain.item.service;

import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberStatus;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.item.dto.response.ChallengeCharacterResponse;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.*;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.user.service.UserService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ChallengeRepository challengeRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final UserService userService;
    private final BusinessClock businessClock;

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
        List<UserItem> equippedItems = userItemRepository.findByUserIdAndEquippedSlotNotNull(userId);

        return new CharacterResponse(toSlotMap(equippedItems, Pose.DEFAULT));
    }

    // 챌린지 멤버 캐릭터 조회
    public List<ChallengeCharacterResponse> getChallengeCharacters(Long challengeId, Long actorId) {
        Challenge challenge = challengeRepository
                .findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND));
        ChallengeGroup group = challengeGroupRepository
                .findById(challenge.getGroupId())
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, actorId)
                .filter(member -> member.getStatus() == ChallengeMemberStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHALLENGE_NOT_MEMBER));

        List<Long> userIds =
                challengeMemberRepository
                        .findAllByChallengeIdAndStatus(challengeId, ChallengeMemberStatus.ACTIVE)
                        .stream()
                        .sorted(Comparator.comparing(ChallengeMember::getId))
                        .map(ChallengeMember::getUserId)
                        .toList();
        boolean ended = challenge.getStatus() == ChallengeStatus.ENDED;
        Set<Long> completedUserIds = ended
                ? Set.of()
                : new HashSet<>(checkInRepository.findCompletedUserIds(
                        challengeId, businessDate(), challenge.getDailyCheckInCount()));
        Map<Long, String> nicknames = userService.findNicknames(userIds);
        Map<Long, List<UserItem>> equippedByUser =
                userItemRepository.findByUserIdInAndEquippedSlotNotNull(userIds).stream()
                        .collect(Collectors.groupingBy(UserItem::getUserId));

        List<ChallengeCharacterResponse> responseList = new ArrayList<>();
        for (Long userId : userIds) {
            Pose pose = Pose.of(group.getMapType(), ended || completedUserIds.contains(userId));
            Map<ItemSlot, String> slots = toSlotMap(equippedByUser.getOrDefault(userId, List.of()), pose);
            responseList.add(new ChallengeCharacterResponse(userId, nicknames.get(userId), pose, slots));
        }

        return responseList;
    }

    private LocalDate businessDate() {
        return businessClock.today();
    }

    // 여러 유저 캐릭터 조회(DEFAULT 자세 고정)
    public Map<Long, Map<ItemSlot, String>> getCharacters(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<UserItem>> equippedByUser =
                userItemRepository.findByUserIdInAndEquippedSlotNotNull(userIds).stream()
                        .collect(Collectors.groupingBy(UserItem::getUserId));

        Map<Long, Map<ItemSlot, String>> characters = new HashMap<>();
        for (Long userId : userIds) {
            characters.put(userId, toSlotMap(equippedByUser.getOrDefault(userId, List.of()), Pose.DEFAULT));
        }

        return characters;
    }

    private Map<ItemSlot, String> toSlotMap(List<UserItem> equippedItems, Pose pose) {
        Map<ItemSlot, String> slotMap = new HashMap<>();
        for (ItemSlot slot : ItemSlot.values()) {
            slotMap.put(slot, null);
        }
        for (UserItem userItem : equippedItems) {
            String key = userItem.getItem().imageKeyForPose(pose);
            String url = key != null ? storageService.publicUrl(key) : null;
            slotMap.put(userItem.getEquippedSlot(), url);
        }

        return slotMap;
    }

    private UserItemResponse toUserItemResponse(UserItem userItem, Pose pose) {
        Item item = userItem.getItem();
        String key = userItem.getItem().imageKeyForPose(pose);
        String url = key != null ? storageService.publicUrl(key) : null;
        return new UserItemResponse(userItem, new ItemResponse(item, url));
    }
}

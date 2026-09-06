package com.gommit.domain.background.service;

import com.gommit.domain.background.dto.response.GroupBackgroundResponse;
import com.gommit.domain.background.dto.response.ShopBackgroundResponse;
import com.gommit.domain.background.entity.Background;
import com.gommit.domain.background.entity.GroupBackground;
import com.gommit.domain.background.entity.GroupBackgroundStatus;
import com.gommit.domain.background.entity.PurchaseRequestStatus;
import com.gommit.domain.background.repository.BackgroundPurchaseRequestRepository;
import com.gommit.domain.background.repository.BackgroundRepository;
import com.gommit.domain.background.repository.GroupBackgroundRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BackgroundService {

    private final BackgroundRepository backgroundRepository;
    private final GroupBackgroundRepository groupBackgroundRepository;
    private final BackgroundPurchaseRequestRepository purchaseRequestRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final StorageService storageService;

    // 그룹 상점 조회
    public SliceResponse<ShopBackgroundResponse> getShopBackgrounds(
            Long groupId, Long userId, Boolean owned, Long cursor, int size) {
        ChallengeGroup group = getGroup(groupId);
        validateActiveMember(groupId, userId);

        List<Background> backgrounds = findByOwnedFilter(groupId, group.getMapType(), owned, cursor, size);

        Map<Long, GroupBackground> ownedMap = new HashMap<>();
        for (GroupBackground groupBackground : groupBackgroundRepository.findAllByGroupId(groupId)) {
            ownedMap.put(groupBackground.getBackground().getId(), groupBackground);
        }

        Long votingBackgroundId = purchaseRequestRepository
                .findByGroupIdAndStatus(groupId, PurchaseRequestStatus.VOTING)
                .map(request -> request.getBackground().getId())
                .orElse(null);

        List<ShopBackgroundResponse> rows = new ArrayList<>();
        for (Background background : backgrounds) {
            GroupBackground groupBackground = ownedMap.get(background.getId());
            rows.add(new ShopBackgroundResponse(
                    background,
                    storageService.publicUrl(background.getImageKey()),
                    groupBackground != null,
                    groupBackground != null && groupBackground.getStatus() == GroupBackgroundStatus.ACTIVE,
                    background.getId().equals(votingBackgroundId)));
        }

        return SliceResponse.ofCursor(rows, size, ShopBackgroundResponse::backgroundId);
    }

    // 그룹 배경 조회
    public GroupBackgroundResponse getActiveBackground(Long groupId) {
        ChallengeGroup group = getGroup(groupId);

        Optional<GroupBackground> active =
                groupBackgroundRepository.findByGroupIdAndStatus(groupId, GroupBackgroundStatus.ACTIVE);

        return active.map(GroupBackground::getBackground)
                .map(background ->
                        new GroupBackgroundResponse(background, storageService.publicUrl(background.getImageKey())))
                .orElseGet(() -> new GroupBackgroundResponse(group.getMapType()));
    }

    // 그룹 배경 적용
    @Transactional
    public GroupBackgroundResponse applyBackground(Long groupId, Long userId, Long backgroundId) {
        ChallengeGroup group = getGroup(groupId);
        validateActiveMember(groupId, userId);

        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        GroupBackground target = groupBackgroundRepository
                .findByGroupIdAndBackgroundId(groupId, backgroundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BACKGROUND_NOT_OWNED));

        groupBackgroundRepository
                .findByGroupIdAndStatus(groupId, GroupBackgroundStatus.ACTIVE)
                .ifPresent(GroupBackground::deactivate);
        target.activate();

        Background background = target.getBackground();
        return new GroupBackgroundResponse(background, storageService.publicUrl(background.getImageKey()));
    }

    // 필터별 판매 배경 한 페이지
    private List<Background> findByOwnedFilter(Long groupId, MapType mapType, Boolean owned, Long cursor, int size) {
        long from = cursor != null ? cursor : 0L;
        Pageable pageable = PageRequest.of(0, size + 1);

        if (owned == null) {
            return backgroundRepository.findByMapTypeAndIdGreaterThanOrderByIdAsc(mapType, from, pageable);
        }
        if (owned) {
            return backgroundRepository.findOwnedByGroupAndMapType(groupId, mapType, from, pageable);
        }
        return backgroundRepository.findNotOwnedByGroupAndMapType(groupId, mapType, from, pageable);
    }

    // 그룹 조회
    private ChallengeGroup getGroup(Long groupId) {
        return challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    // 활동 중인 멤버 확인
    private void validateActiveMember(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }
}

package com.gommit.domain.background.service;

import com.gommit.domain.background.dto.response.PurchaseRequestResponse;
import com.gommit.domain.background.entity.Background;
import com.gommit.domain.background.entity.BackgroundPurchaseRequest;
import com.gommit.domain.background.entity.BackgroundPurchaseVote;
import com.gommit.domain.background.entity.GroupBackground;
import com.gommit.domain.background.entity.GroupBackgroundStatus;
import com.gommit.domain.background.entity.PurchaseRequestStatus;
import com.gommit.domain.background.repository.BackgroundPurchaseRequestRepository;
import com.gommit.domain.background.repository.BackgroundPurchaseVoteRepository;
import com.gommit.domain.background.repository.BackgroundRepository;
import com.gommit.domain.background.repository.GroupBackgroundRepository;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.GroupStatus;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.point.entity.GroupPointReason;
import com.gommit.domain.point.service.PointService;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BackgroundPurchaseService {

    private static final int VOTING_DAYS = 3;

    private final BackgroundRepository backgroundRepository;
    private final GroupBackgroundRepository groupBackgroundRepository;
    private final BackgroundPurchaseRequestRepository purchaseRequestRepository;
    private final BackgroundPurchaseVoteRepository voteRepository;
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PointService pointService;
    private final StorageService storageService;

    // 구매 제안
    public PurchaseRequestResponse createRequest(Long groupId, Long userId, Long backgroundId) {
        ChallengeGroup group = challengeGroupRepository
                .findByIdWithLock(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        if (group.getStatus() == GroupStatus.ENDED) {
            throw new BusinessException(ErrorCode.GROUP_ENDED);
        }
        validateActiveMember(groupId, userId);

        Background background = backgroundRepository
                .findById(backgroundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BACKGROUND_NOT_FOUND));

        if (background.getMapType() != group.getMapType()) {
            throw new BusinessException(ErrorCode.BACKGROUND_MAP_TYPE_MISMATCH);
        }
        if (groupBackgroundRepository.existsByGroupIdAndBackgroundId(groupId, backgroundId)) {
            throw new BusinessException(ErrorCode.BACKGROUND_ALREADY_OWNED);
        }

        LocalDateTime now = LocalDateTime.now();
        closeExpired(groupId, now);

        if (purchaseRequestRepository
                .findByGroupIdAndStatus(groupId, PurchaseRequestStatus.VOTING)
                .isPresent()) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_ALREADY_EXISTS);
        }
        if (findGroupBalance(groupId) < background.getPrice()) {
            throw new BusinessException(ErrorCode.POINT_INSUFFICIENT);
        }

        BackgroundPurchaseRequest request = purchaseRequestRepository.save(BackgroundPurchaseRequest.builder()
                .groupId(groupId)
                .background(background)
                .requestedBy(userId)
                .expiresAt(now.plusDays(VOTING_DAYS))
                .build());

        voteRepository.save(BackgroundPurchaseVote.builder()
                .request(request)
                .userId(userId)
                .agreed(true)
                .build());

        countVotes(request);
        return toPurchaseRequestResponse(request, userId);
    }

    // 투표
    public PurchaseRequestResponse vote(Long groupId, Long userId, Long requestId, boolean agreed) {
        validateActiveMember(groupId, userId);

        BackgroundPurchaseRequest request = lockRequest(groupId, requestId);
        if (!request.isVoting()) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_CLOSED);
        }

        if (request.isExpired(LocalDateTime.now()) || getGroup(groupId).getStatus() == GroupStatus.ENDED) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_CLOSED);
        }
        if (voteRepository.existsByRequestIdAndUserId(requestId, userId)) {
            throw new BusinessException(ErrorCode.VOTE_ALREADY_CAST);
        }

        voteRepository.save(BackgroundPurchaseVote.builder()
                .request(request)
                .userId(userId)
                .agreed(agreed)
                .build());

        countVotes(request);
        return toPurchaseRequestResponse(request, userId);
    }

    // 제안자 또는 그룹 OWNER 가 요청을 취소
    public void cancelRequest(Long groupId, Long userId, Long requestId) {
        validateActiveMember(groupId, userId);

        BackgroundPurchaseRequest request = lockRequest(groupId, requestId);
        if (!request.isVoting()) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_CLOSED);
        }
        if (!request.getRequestedBy().equals(userId)
                && !getGroup(groupId).getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_NOT_CANCELABLE);
        }

        request.reject();
    }

    // 진행 중인 제안 확인
    public Optional<PurchaseRequestResponse> getCurrentRequest(Long groupId, Long userId) {
        validateActiveMember(groupId, userId);
        closeExpired(groupId, LocalDateTime.now());

        return purchaseRequestRepository
                .findByGroupIdAndStatus(groupId, PurchaseRequestStatus.VOTING)
                .map(request -> toPurchaseRequestResponse(request, userId));
    }

    // 투표 재집계
    public void recountVotes(Long groupId) {
        Optional<BackgroundPurchaseRequest> voting =
                purchaseRequestRepository.findWithLockByGroupIdAndStatus(groupId, PurchaseRequestStatus.VOTING);
        if (voting.isEmpty()) {
            return;
        }

        BackgroundPurchaseRequest request = voting.get();
        if (request.isExpired(LocalDateTime.now()) || getGroup(groupId).getStatus() == GroupStatus.ENDED) {
            request.reject();
            return;
        }

        countVotes(request);
    }

    // 투표 집계와 판정
    private void countVotes(BackgroundPurchaseRequest request) {
        Set<Long> activeUserIds = findActiveUserIds(request.getGroupId());

        int totalMembers = activeUserIds.size();
        if (totalMembers == 0) {
            request.reject();
            return;
        }

        int agree = 0;
        int voted = 0;
        for (BackgroundPurchaseVote vote : voteRepository.findAllByRequestId(request.getId())) {
            if (!activeUserIds.contains(vote.getUserId())) {
                continue;
            }
            voted++;
            if (vote.isAgreed()) {
                agree++;
            }
        }

        int required = totalMembers / 2 + 1;
        if (agree >= required) {
            purchase(request);
            return;
        }
        if (agree + (totalMembers - voted) < required) {
            request.reject();
        }
    }

    // 결제
    private void purchase(BackgroundPurchaseRequest request) {
        Long groupId = request.getGroupId();
        Background background = request.getBackground();

        if (findGroupBalance(groupId) < background.getPrice()) {
            request.reject();
            return;
        }

        pointService.deductGroup(
                groupId, background.getPrice(), GroupPointReason.BACKGROUND_PURCHASE, background.getName());

        GroupBackground purchased = groupBackgroundRepository.save(GroupBackground.builder()
                .groupId(groupId)
                .background(background)
                .build());

        groupBackgroundRepository
                .findByGroupIdAndStatus(groupId, GroupBackgroundStatus.ACTIVE)
                .ifPresent(GroupBackground::deactivate);
        purchased.activate();

        request.approve();
    }

    // 기한 지난 제안 부결
    private void closeExpired(Long groupId, LocalDateTime now) {
        purchaseRequestRepository
                .findByGroupIdAndStatus(groupId, PurchaseRequestStatus.VOTING)
                .filter(request -> request.isExpired(now))
                .ifPresent(BackgroundPurchaseRequest::reject);
    }

    // 제안 잠금
    private BackgroundPurchaseRequest lockRequest(Long groupId, Long requestId) {
        BackgroundPurchaseRequest request = purchaseRequestRepository
                .findWithLockById(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_REQUEST_NOT_FOUND));

        if (!request.getGroupId().equals(groupId)) {
            throw new BusinessException(ErrorCode.PURCHASE_REQUEST_NOT_FOUND);
        }
        return request;
    }

    // 그룹 조회
    private ChallengeGroup getGroup(Long groupId) {
        return challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
    }

    // 활동 중인 멤버 id
    private Set<Long> findActiveUserIds(Long groupId) {
        Set<Long> userIds = new HashSet<>();
        for (GroupMember member : groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE)) {
            userIds.add(member.getUserId());
        }
        return userIds;
    }

    // 그룹 포인트 잔액
    private int findGroupBalance(Long groupId) {
        return pointService.getGroupBalance(groupId).balance();
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

    // 응답 조립
    private PurchaseRequestResponse toPurchaseRequestResponse(BackgroundPurchaseRequest request, Long userId) {
        Set<Long> activeUserIds = findActiveUserIds(request.getGroupId());

        int agree = 0;
        int disagree = 0;
        boolean voted = false;
        for (BackgroundPurchaseVote vote : voteRepository.findAllByRequestId(request.getId())) {
            if (vote.getUserId().equals(userId)) {
                voted = true;
            }
            if (!activeUserIds.contains(vote.getUserId())) {
                continue;
            }
            if (vote.isAgreed()) {
                agree++;
            } else {
                disagree++;
            }
        }

        int totalMembers = activeUserIds.size();
        String nickname = userRepository
                .findById(request.getRequestedBy())
                .map(user -> user.getNickname())
                .orElse(null);

        return new PurchaseRequestResponse(
                request,
                storageService.publicUrl(request.getBackground().getImageKey()),
                nickname,
                agree,
                disagree,
                totalMembers,
                totalMembers / 2 + 1,
                voted);
    }
}

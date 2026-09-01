package com.gommit.domain.group.service;

import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.ChallengeMemberRole;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.group.dto.response.*;
import com.gommit.domain.group.entity.*;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberCount;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final ChallengeMemberRepository challengeMemberRepository;

    @Transactional
    public GroupDetailResponse createGroup(Long userId, GroupCreateRequest request) {
        validateCategoryMapType(request);

        // 챌린지 그룹 초기 저장
        ChallengeGroup group = createGroupEntity(userId, request);

        // 생성자를 그룹 멤버로 등록
        GroupMember groupMember = createGroupMember(group, userId);

        // 그룹 생성 시 챌린지 설정
        InitialChallengeSettingRequest setting = request.challenge();

        // 첫 챌린지 생성
        Challenge challenge = challengeService.createInitialChallenge(group.getId(), userId, setting);

        ChallengeSummaryResponse challengeResponse = new ChallengeSummaryResponse(challenge, setting);

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new GroupDetailResponse(
            new GroupResponse(group, 1),
            challengeResponse,
            new GroupMemberResponse(groupMember, user)
        );
    }

    // 그룹 생성
    private ChallengeGroup createGroupEntity(Long userId, GroupCreateRequest request) {
        // 그룹 생성 시 그룹 상태는 READY로 설정
        ChallengeGroup group = ChallengeGroup.builder()
            .name(request.name())
            .description(request.description())
            .category(request.category())
            .mapType(request.mapType())
            .visibility(request.visibility())
            .maxMembers(request.maxMembers())
            .ownerId(userId)
            .build();

        return challengeGroupRepository.save(group);
    }

    // 그룹 생성자 첫 번째 그룹 멤버로 지정(초기 상태는 ACTIVE)
    private GroupMember createGroupMember(ChallengeGroup group, Long userId) {
        GroupMember groupMember = GroupMember.builder()
            .group(group)
            .userId(userId)
            .build();

        return groupMemberRepository.save(groupMember);
    }

    private void validateCategoryMapType(GroupCreateRequest request) {
        boolean valid = switch (request.category()) {
            case DEV -> request.mapType() == MapType.STUDY_ROOM;
            case READING -> request.mapType() == MapType.STUDY_ROOM;
            case JOB -> request.mapType() == MapType.STUDY_ROOM;
            case STUDY -> request.mapType() == MapType.STUDY_ROOM;
            case EXERCISE -> request.mapType() == MapType.GYM;
            case HEALTH -> request.mapType() == MapType.GYM;
            case LIFE -> request.mapType() == MapType.STUDY_ROOM;
            case ETC -> request.mapType() == MapType.STUDY_ROOM;
        };

        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_CATEGORY_MAP_TYPE);
        }

    }

    @Transactional(readOnly = true)
    public GroupSummaryCursorResponse getPublicGroups(String keyword, GroupCategory category, GroupSort sort, Long cursor, int size) {

        // 공개 + 모집 중인 그룹 조회
        List<ChallengeGroup> groups = challengeGroupRepository.findAllByVisibilityAndStatus(Visibility.PUBLIC, GroupStatus.READY);

        // 그룹명 검색
        if (keyword != null && !keyword.isBlank()) {
            String searchKeyword = keyword.trim().toLowerCase();

            groups = groups.stream().filter(group -> group.getName().toLowerCase().contains(searchKeyword)).toList();
        }

        // 카테고리 필터
        if(category != null) {
            groups = groups.stream().filter(group -> group.getCategory() == category).toList();
        }

        // 조회 결과가 없을경우 빈 응답
        if(groups.isEmpty()) {
            return new GroupSummaryCursorResponse(
                List.of(),
                new CursorMetaResponse(null, false, 0)
            );
        }

        // 그룹 ID 목록 추출
        List<Long> groupIds = groups.stream().map(ChallengeGroup::getId).toList();

        // 각 그룹의 READY Challenge 한번에 조회
        List<Challenge> challenges = challengeRepository.findAllByGroupIdInAndStatus(
            groupIds,
            ChallengeStatus.READY
        );

        // groupId -> Challenges
        Map<Long, Challenge> challengeMap = challenges.stream().collect(Collectors.toMap(Challenge::getGroupId, challenge -> challenge));


        // 각 그룹의 ACTIVE 멤버 수를 한 번에 조회
        List<GroupMemberCount> memberCounts = groupMemberRepository.countByGroupIdsAndStatus(groupIds, GroupMemberStatus.ACTIVE);

        // groupId -> currentMembers
        Map<Long, Long> memberCountMap = memberCounts.stream().collect(Collectors.toMap(GroupMemberCount::getGroupId, GroupMemberCount::getCount));

        // GroupSummaryResponse로 변환
        List<GroupSummaryResponse> summaries = groups.stream().map(group -> {
            Challenge challenge = challengeMap.get(group.getId());

            if(challenge == null) {
                throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
            }

            int currentMembers = memberCountMap.getOrDefault(group.getId(), 0L).intValue();

            return new GroupSummaryResponse(group, challenge, currentMembers);
        }).toList();

        // 정렬하기
        summaries = sortGroupSummaries(summaries, sort);

        // cursor 적용
        if(cursor != null) {
            summaries = summaries.stream().filter(summary -> summary.id() < cursor).toList();
        }

        // size + 1 방식으로 다음 페이지 존재 여부 확인
        boolean hasNext = summaries.size() > size;

        List<GroupSummaryResponse> content = summaries.stream().limit(size).toList();

        // 다음 cursor 계산
        Long nextCursor = null;

        if(hasNext && !content.isEmpty()) {
            nextCursor = content.get(content.size() - 1).id();
        }

        // cursor meta 생성
        CursorMetaResponse meta = new CursorMetaResponse(
            nextCursor,
            hasNext,
            content.size()
        );

        return new GroupSummaryCursorResponse(content, meta);
    }

    private List<GroupSummaryResponse> sortGroupSummaries(
        List<GroupSummaryResponse> summaries,
        GroupSort sort
    ) {

        return switch (sort) {

            // 최신 생성 그룹
            case LATEST -> summaries.stream()
                .sorted(
                    Comparator.comparing(GroupSummaryResponse::id)
                        .reversed()
                )
                .toList();

            // 현재 참여 인원이 많은 그룹
            case POPULAR -> summaries.stream()
                .sorted(
                    Comparator.comparingInt(
                            GroupSummaryResponse::currentMembers
                        )
                        .reversed()
                        .thenComparing(
                            GroupSummaryResponse::id,
                            Comparator.reverseOrder()
                        )
                )
                .toList();

            // 시작일이 가까운 그룹
            case START_SOON -> summaries.stream()
                .sorted(
                    Comparator.comparing(
                            GroupSummaryResponse::startDate
                        )
                        .thenComparing(
                            GroupSummaryResponse::id,
                            Comparator.reverseOrder()
                        )
                )
                .toList();
        };
    }


    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(Long groupId) {

        // 그룹 조회
        ChallengeGroup group = challengeGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        // 현재 챌린지 조회
        Challenge currentChallenge = challengeRepository.findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE).orElseGet(() -> challengeRepository.findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY).orElse(null));

        // 현재 그룹 멤버 조회
        List<GroupMember> members = groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);

        List<Long> userIds = members.stream().map(GroupMember::getUserId).toList();

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));

        // 그룹정보랑 유저정보 -> 응답 DTO
        List<GroupMemberResponse> memberResponses = members.stream().map(member -> {
            User user = userMap.get(member.getUserId());

            if(user == null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }

            return new GroupMemberResponse(member, user);
        }).toList();

        return new GroupDetailResponse(
            new GroupResponse(group, members.size()),
            currentChallenge == null ? null : new ChallengeSummaryResponse(currentChallenge),
            memberResponses
        );
    }

    // 공개 그룹 참여
    @Transactional
    public GroupJoinResponse joinGroup(Long groupId, Long userId) {
        // 그룹조회
        ChallengeGroup group = challengeGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        // 그룹 참여 가능한지 확인
        if(group.getStatus() != GroupStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_NOT_JOINABLE);
        }

        // 공개그룹인지 확인
        if(group.getVisibility() != Visibility.PUBLIC) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }

        // READY 챌린지 조회
        Challenge challenge = challengeRepository.findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_JOINABLE));

        // 기존 GroupMember 참여 이력 확인
        if(groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }

        // 현재 ACTIVE 멤버 수 확인
        long currentMembers = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);

        if(currentMembers >= group.getMaxMembers()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }

        // 검증 후 GroupMember 생성
        GroupMember groupMember = GroupMember.builder()
            .group(group)
            .userId(userId)
            .build();

        GroupMember savedGroupMember = groupMemberRepository.save(groupMember);

        // ChallengeMember 생성
        ChallengeMember challengeMember = challengeService.createChallengeMember(
            challenge,
            userId,
            ChallengeMemberRole.MEMBER
        );

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return new GroupJoinResponse(
            new GroupMemberResponse(savedGroupMember, user),
            challenge.getId(),
            challengeMember.getId()
        );
    }
}

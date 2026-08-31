package com.gommit.domain.group.service;

import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeStatus;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.group.dto.response.GroupDetailResponse;
import com.gommit.domain.group.dto.response.GroupMemberResponse;
import com.gommit.domain.group.dto.response.GroupResponse;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

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
    public GroupDetailResponse getGroupDetail(Long groupId) {

        // 그룹 조회
        ChallengeGroup group = challengeGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        // 현재 챌린지 조회
        Challenge currentChallenge = challengeRepository.findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE).orElseGet(() -> challengeRepository.findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY).orElse(null));

        // 현재 그룹 멤버 조회
        List<GroupMember> members = groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);

        // 그룹정보랑 유저정보 -> 응답 DTO
        List<GroupMemberResponse> memberResponses = members.stream().map(member -> {
            User user = userRepository.findById(member.getUserId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            return new GroupMemberResponse(member, user);
        }).toList();

        return new GroupDetailResponse(
            new GroupResponse(group, members.size()),
            currentChallenge == null ? null : new ChallengeSummaryResponse(currentChallenge),
            memberResponses
        );
    }
}

package com.gommit.domain.group.service;

import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.Challenge;
import com.gommit.domain.challenge.entity.ChallengeMember;
import com.gommit.domain.challenge.entity.Weekday;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.checkin.entity.CheckInType;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.group.dto.response.GroupDetailResponse;
import com.gommit.domain.group.dto.response.GroupMemberResponse;
import com.gommit.domain.group.dto.response.GroupResponse;
import com.gommit.domain.group.entity.ChallengeGroup;
import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.MapType;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeService challengeService;

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

        // TODO: User 도메인 연동 후 실제 nickname 조회
        String nickname = "임시닉네임";

        return new GroupDetailResponse(
            new GroupResponse(group, 1),
            challengeResponse,
            new GroupMemberResponse(groupMember, nickname)
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


}

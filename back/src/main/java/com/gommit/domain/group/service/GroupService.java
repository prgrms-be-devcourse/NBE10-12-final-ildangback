package com.gommit.domain.group.service;

import com.gommit.domain.challenge.dto.request.InitialChallengeSettingRequest;
import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;
import com.gommit.domain.challenge.entity.*;
import com.gommit.domain.challenge.repository.ChallengeMemberRepository;
import com.gommit.domain.challenge.repository.ChallengeRepository;
import com.gommit.domain.challenge.service.ChallengeMemberService;
import com.gommit.domain.challenge.service.ChallengeProgressCalculator;
import com.gommit.domain.challenge.service.ChallengeService;
import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.group.dto.request.GroupCreateRequest;
import com.gommit.domain.group.dto.response.*;
import com.gommit.domain.group.entity.*;
import com.gommit.domain.group.repository.ChallengeGroupRepository;
import com.gommit.domain.group.repository.GroupMemberCount;
import com.gommit.domain.group.repository.GroupMemberRepository;
import com.gommit.domain.point.service.PersonalPointService;
import com.gommit.domain.user.entity.User;
import com.gommit.domain.user.repository.UserRepository;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import com.gommit.global.time.BusinessClock;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {
    private final ChallengeGroupRepository challengeGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ChallengeService challengeService;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final ChallengeMemberRepository challengeMemberRepository;
    private final CheckInRepository checkInRepository;
    private final ChallengeMemberService challengeMemberService;
    private final ChallengeProgressCalculator challengeProgressCalculator;
    private final PersonalPointService personalPointService;
    private final BusinessClock businessClock;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 6;

    @Transactional
    public GroupDetailResponse createGroup(Long userId, GroupCreateRequest request) {
        validateCategoryMapType(request);
        ChallengeGroup group = createGroupEntity(userId, request);
        GroupMember groupMember = createGroupMember(group, userId);
        InitialChallengeSettingRequest setting = request.challenge();
        Challenge challenge = challengeService.createInitialChallenge(group.getId(), userId, setting);
        ChallengeSummaryResponse challengeResponse = new ChallengeSummaryResponse(challenge, setting);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new GroupDetailResponse(
                new GroupResponse(group, 1), challengeResponse, new GroupMemberResponse(groupMember, user));
    }

    private ChallengeGroup createGroupEntity(Long userId, GroupCreateRequest request) {
        String inviteCode = null;
        if (request.visibility() == Visibility.CODE_ONLY) {
            inviteCode = generateUniqueInviteCode();
        }
        // 그룹 생성 시 그룹 상태는 READY로 설정
        ChallengeGroup group = ChallengeGroup.builder()
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .mapType(request.mapType())
                .visibility(request.visibility())
                .maxMembers(request.maxMembers())
                .ownerId(userId)
                .inviteCode(inviteCode)
                .build();
        return challengeGroupRepository.save(group);
    }

    private String generateInviteCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int index = SECURE_RANDOM.nextInt(INVITE_CODE_CHARACTERS.length());
            code.append(INVITE_CODE_CHARACTERS.charAt(index));
        }
        return code.toString();
    }

    private String generateUniqueInviteCode() {
        String inviteCode;
        do {
            inviteCode = generateInviteCode();
        } while (challengeGroupRepository.existsByInviteCode(inviteCode));
        return inviteCode;
    }

    // 그룹 생성자 첫 번째 그룹 멤버로 지정(초기 상태는 ACTIVE)
    private GroupMember createGroupMember(ChallengeGroup group, Long userId) {
        GroupMember groupMember =
                GroupMember.builder().group(group).userId(userId).build();
        return groupMemberRepository.save(groupMember);
    }

    private void validateCategoryMapType(GroupCreateRequest request) {
        boolean valid =
                switch (request.category()) {
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
    public SliceResponse<GroupSummaryResponse> getPublicGroups(
            String keyword, GroupCategory category, GroupSort sort, Long cursor, int size) {
        List<ChallengeGroup> groups =
                challengeGroupRepository.findAllByVisibilityAndStatus(Visibility.PUBLIC, GroupStatus.READY);
        if (keyword != null && !keyword.isBlank()) {
            String searchKeyword = keyword.trim().toLowerCase();

            groups = groups.stream()
                    .filter(group -> group.getName().toLowerCase().contains(searchKeyword))
                    .toList();
        }
        if (category != null) {
            groups = groups.stream()
                    .filter(group -> group.getCategory() == category)
                    .toList();
        }
        if (groups.isEmpty()) {
            return new SliceResponse<>(List.of(), false, null);
        }
        List<Long> groupIds = groups.stream().map(ChallengeGroup::getId).toList();
        List<Challenge> challenges = challengeRepository.findAllByGroupIdInAndStatus(groupIds, ChallengeStatus.READY);
        Map<Long, Challenge> challengeMap =
                challenges.stream().collect(Collectors.toMap(Challenge::getGroupId, challenge -> challenge));
        List<GroupMemberCount> memberCounts =
                groupMemberRepository.countByGroupIdsAndStatus(groupIds, GroupMemberStatus.ACTIVE);
        Map<Long, Long> memberCountMap = memberCounts.stream()
                .collect(Collectors.toMap(GroupMemberCount::getGroupId, GroupMemberCount::getCount));
        List<GroupSummaryResponse> summaries = groups.stream()
                .map(group -> {
                    Challenge challenge = challengeMap.get(group.getId());
                    if (challenge == null) {
                        throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
                    }
                    int currentMembers =
                            memberCountMap.getOrDefault(group.getId(), 0L).intValue();
                    return new GroupSummaryResponse(group, challenge, currentMembers);
                })
                .toList();
        summaries = sortGroupSummaries(summaries, sort);
        summaries = applyCursor(summaries, cursor);
        return SliceResponse.ofCursor(summaries, size, GroupSummaryResponse::id);
    }

    private List<GroupSummaryResponse> sortGroupSummaries(List<GroupSummaryResponse> summaries, GroupSort sort) {
        return switch (sort) {
            case LATEST ->
                summaries.stream()
                        .sorted(Comparator.comparing(GroupSummaryResponse::id).reversed())
                        .toList();
            case POPULAR ->
                summaries.stream()
                        .sorted(Comparator.comparingInt(GroupSummaryResponse::currentMembers)
                                .reversed()
                                .thenComparing(Comparator.comparing(GroupSummaryResponse::id)
                                        .reversed()))
                        .toList();
            case START_SOON ->
                summaries.stream()
                        .sorted(Comparator.comparing(GroupSummaryResponse::startDate)
                                .thenComparing(GroupSummaryResponse::id, Comparator.reverseOrder()))
                        .toList();
        };
    }

    private List<GroupSummaryResponse> applyCursor(List<GroupSummaryResponse> summaries, Long cursor) {
        if (cursor == null) {
            return summaries;
        }
        for (int i = 0; i < summaries.size(); i++) {
            if (summaries.get(i).id().equals(cursor)) {
                return summaries.subList(i + 1, summaries.size());
            }
        }
        return List.of();
    }

    @Transactional(readOnly = true)
    public GroupDetailResponse getGroupDetail(Long groupId, Long userId) {
        ChallengeGroup group = challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        validateGroupDetailAccess(group, userId);
        Challenge currentChallenge = challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE)
                .orElseGet(() -> challengeRepository
                        .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY)
                        .orElse(null));
        List<GroupMember> members = groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);
        List<Long> userIds = members.stream().map(GroupMember::getUserId).toList();
        Map<Long, User> userMap =
                userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, user -> user));
        List<GroupMemberResponse> memberResponses = members.stream()
                .map(member -> {
                    User user = userMap.get(member.getUserId());
                    if (user == null) {
                        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
                    }
                    return new GroupMemberResponse(member, user);
                })
                .toList();
        return new GroupDetailResponse(
                new GroupResponse(group, members.size()),
                currentChallenge == null ? null : new ChallengeSummaryResponse(currentChallenge),
                memberResponses);
    }

    private void validateGroupDetailAccess(ChallengeGroup group, Long userId) {
        // 공개 모집 중인 그룹은 가입 전에도 상세 조회 가능
        if (group.getVisibility() == Visibility.PUBLIC && group.getStatus() == GroupStatus.READY) {
            return;
        }
        GroupMember member = groupMemberRepository
                .findByGroupIdAndUserId(group.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
        if (member.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
    }

    @Transactional
    public GroupJoinResponse joinGroup(Long groupId, Long userId) {
        ChallengeGroup group = challengeGroupRepository
                .findByIdWithLock(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (group.getStatus() != GroupStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_NOT_JOINABLE);
        }
        if (group.getVisibility() != Visibility.PUBLIC) {
            throw new BusinessException(ErrorCode.INVITE_CODE_REQUIRED);
        }
        Challenge challenge = challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_JOINABLE));
        if (groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        long currentMembers = groupMemberRepository.countByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE);

        if (currentMembers >= group.getMaxMembers()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }
        GroupMember groupMember =
                GroupMember.builder().group(group).userId(userId).build();
        GroupMember savedGroupMember = groupMemberRepository.save(groupMember);
        ChallengeMember challengeMember =
                challengeMemberService.createChallengeMember(challenge, userId, ChallengeMemberRole.MEMBER);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new GroupJoinResponse(
                new GroupMemberResponse(savedGroupMember, user), challenge.getId(), challengeMember.getId());
    }

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        ChallengeGroup group = challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
        if (groupMember.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
        if (group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_CANNOT_LEAVE);
        }
        groupMember.leave();
        challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE)
                .ifPresent(challenge -> leaveChallengeMember(challenge.getId(), userId, group.getName()));
        challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.READY)
                .ifPresent(challenge -> leaveChallengeMember(challenge.getId(), userId, group.getName()));
    }

    private void leaveChallengeMember(Long challengeId, Long userId, String groupName) {
        challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .filter(member -> member.getStatus() == ChallengeMemberStatus.ACTIVE)
                .ifPresent(member -> {
                    member.leave();
                    // 중도 탈퇴 - 이 챌린지에서 번 포인트 회수(Should). READY 챌린지는 아직
                    // 체크인이 없어 번 게 없으니 자연히 아무 일도 안 일어난다.
                    personalPointService.recoverChallengePoints(userId, challengeId, groupName);
                });
    }

    @Transactional(readOnly = true)
    public SliceResponse<MyGroupSummaryResponse> getMyGroups(Long userId, GroupStatus status, Long cursor, int size) {
        List<ChallengeMember> challengeMembers =
                challengeMemberRepository.findAllByUserIdAndStatus(userId, ChallengeMemberStatus.ACTIVE);
        List<Long> currentGroupIds = challengeMembers.stream()
                .map(ChallengeMember::getChallenge)
                .filter(challenge -> challenge.getStatus() == ChallengeStatus.READY
                        || challenge.getStatus() == ChallengeStatus.ACTIVE)
                .map(Challenge::getGroupId)
                .distinct()
                .toList();
        List<ChallengeMember> displayMembers = challengeMembers.stream()
                .filter(member -> {
                    Challenge challenge = member.getChallenge();
                    if (challenge.getStatus() == ChallengeStatus.READY
                            || challenge.getStatus() == ChallengeStatus.ACTIVE) {
                        return true;
                    }
                    if (challenge.getStatus() == ChallengeStatus.ENDED) {
                        // 같은 그룹의 다음시즌에 현재 참여 중이라면 이전 챌린지 영역에 중복 표시하지 않음
                        return !currentGroupIds.contains(challenge.getGroupId());
                    }
                    return false;
                })
                .toList();
        List<Long> groupIds = displayMembers.stream()
                .map(member -> member.getChallenge().getGroupId())
                .distinct()
                .toList();
        Map<Long, ChallengeGroup> groupMap = challengeGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ChallengeGroup::getId, Function.identity()));
        if (status != null) {
            displayMembers = displayMembers.stream()
                    .filter(member -> {
                        Long groupId = member.getChallenge().getGroupId();
                        ChallengeGroup group = groupMap.get(groupId);
                        if (group == null) {
                            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
                        }
                        return group.getStatus() == status;
                    })
                    .toList();
        }
        displayMembers = displayMembers.stream()
                .sorted(Comparator.comparing(ChallengeMember::getId).reversed())
                .toList();
        if (cursor != null) {
            displayMembers = displayMembers.stream()
                    .filter(member -> member.getId() < cursor)
                    .toList();
        }
        // hasNext 확인을 위해 size + 1 개 조회
        List<ChallengeMember> pageMembers =
                displayMembers.stream().limit(size + 1L).toList();
        boolean hasNext = pageMembers.size() > size;
        if (hasNext) {
            pageMembers = pageMembers.subList(0, size);
        }
        List<MyGroupSummaryResponse> content = pageMembers.stream()
                .map(member -> {
                    Long groupId = member.getChallenge().getGroupId();
                    ChallengeGroup group = groupMap.get(groupId);

                    if (group == null) {
                        throw new BusinessException(ErrorCode.GROUP_NOT_FOUND);
                    }
                    return toMyGroupSummaryResponse(member, userId, group);
                })
                .toList();
        Long nextCursor = null;
        if (hasNext && !pageMembers.isEmpty()) {
            nextCursor = pageMembers.get(pageMembers.size() - 1).getId();
        }
        return new SliceResponse<>(content, hasNext, nextCursor);
    }

    private MyGroupSummaryResponse toMyGroupSummaryResponse(ChallengeMember member, Long userId, ChallengeGroup group) {
        Challenge challenge = member.getChallenge();
        int participantCount = (int)
                challengeMemberRepository.countByChallengeIdAndStatus(challenge.getId(), ChallengeMemberStatus.ACTIVE);
        LocalDate today = businessClock.today();
        int currentDay = challengeProgressCalculator.calculateCurrentDay(challenge, today);
        int totalDays = challenge.getRequiredDayCount();
        double periodProgressRate = challengeProgressCalculator.calculatePeriodProgressRate(currentDay, totalDays);
        int todayCheckInCount = 0;
        if (challenge.getStatus() == ChallengeStatus.ACTIVE) {
            todayCheckInCount =
                    checkInRepository.countByChallengeIdAndUserIdAndBusinessDate(challenge.getId(), userId, today);
        }
        boolean todayCompleted = challenge.getStatus() == ChallengeStatus.ACTIVE
                && todayCheckInCount >= challenge.getDailyCheckInCount();
        return new MyGroupSummaryResponse(
                group.getId(),
                challenge.getId(),
                group.getName(),
                group.getCategory(),
                group.getStatus(),
                challenge.getStatus(),
                participantCount,
                currentDay,
                totalDays,
                periodProgressRate,
                todayCheckInCount,
                challenge.getDailyCheckInCount(),
                todayCompleted);
    }

    @Transactional
    public void kickMember(Long groupId, Long userId, Long targetUserId) {
        ChallengeGroup group = challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_ONLY);
        }
        if (group.getOwnerId().equals(targetUserId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_CANNOT_BE_KICKED);
        }
        Challenge activeChallenge = challengeRepository
                .findFirstByGroupIdAndStatus(groupId, ChallengeStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_MEMBER_KICK_NOT_ALLOWED));
        GroupMember targetMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));
        if (targetMember.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }
        targetMember.kick();
        kickChallengeMember(activeChallenge.getId(), targetUserId, group.getName());
    }

    private void kickChallengeMember(Long challengeId, Long userId, String groupName) {
        challengeMemberRepository
                .findByChallengeIdAndUserId(challengeId, userId)
                .filter(member -> member.getStatus() == ChallengeMemberStatus.ACTIVE)
                .ifPresent(member -> {
                    member.kick();
                    // 추방도 중도 탈퇴와 동일하게 그 챌린지에서 번 포인트를 회수한다(Should).
                    personalPointService.recoverChallengePoints(userId, challengeId, groupName);
                });
    }

    // 회원 탈퇴 시 그룹 정리
    @Transactional
    public void leaveAllGroupsOnAccountDeletion(Long userId) {
        List<GroupMember> groupMembers =
                groupMemberRepository.findAllByUserIdAndStatus(userId, GroupMemberStatus.ACTIVE);
        for (GroupMember groupMember : groupMembers) {
            groupMember.leave();
            ChallengeGroup group = groupMember.getGroup();
            boolean ownerLeaving = group.getOwnerId().equals(userId);
            Long newOwnerId = ownerLeaving ? pickNewOwner(group.getId(), userId) : null;
            leaveChallengesOnAccountDeletion(group.getId(), userId, newOwnerId, group.getName());
            if (!ownerLeaving) {
                continue;
            }
            if (newOwnerId == null) {
                group.end();
            } else {
                group.changeOwner(newOwnerId);
            }
        }
    }

    // 새 방장 선정
    private Long pickNewOwner(Long groupId, Long userId) {
        List<GroupMember> remainingMembers =
                groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE).stream()
                        .filter(member -> !member.getUserId().equals(userId))
                        .toList();
        return remainingMembers.isEmpty()
                ? null
                : remainingMembers
                        .get(SECURE_RANDOM.nextInt(remainingMembers.size()))
                        .getUserId();
    }

    // 살아 있는 시즌 정리
    private void leaveChallengesOnAccountDeletion(Long groupId, Long userId, Long preferredOwnerId, String groupName) {
        for (ChallengeStatus status : List.of(ChallengeStatus.ACTIVE, ChallengeStatus.READY)) {
            challengeRepository
                    .findFirstByGroupIdAndStatus(groupId, status)
                    .ifPresent(challenge ->
                            leaveChallengeMemberAndDelegate(challenge, userId, preferredOwnerId, groupName));
        }
    }

    // 시즌 이탈과 OWNER 이관
    private void leaveChallengeMemberAndDelegate(
            Challenge challenge, Long userId, Long preferredOwnerId, String groupName) {
        ChallengeMember leavingMember = challengeMemberRepository
                .findByChallengeIdAndUserId(challenge.getId(), userId)
                .filter(member -> member.getStatus() == ChallengeMemberStatus.ACTIVE)
                .orElse(null);
        if (leavingMember == null) {
            return;
        }
        leavingMember.leave();
        // 계정 탈퇴로 인한 이탈도 중도 탈퇴와 동일하게 그 챌린지에서 번 포인트를 회수한다(Should).
        personalPointService.recoverChallengePoints(userId, challenge.getId(), groupName);
        if (leavingMember.getRole() != ChallengeMemberRole.OWNER) {
            return;
        }
        leavingMember.changeRole(ChallengeMemberRole.MEMBER);
        List<ChallengeMember> remainingMembers =
                challengeMemberRepository
                        .findAllByChallengeIdAndStatus(challenge.getId(), ChallengeMemberStatus.ACTIVE)
                        .stream()
                        .filter(member -> !member.getUserId().equals(userId))
                        .toList();
        if (remainingMembers.isEmpty()) {
            challenge.end();
            return;
        }
        ChallengeMember newOwner = remainingMembers.stream()
                .filter(member -> member.getUserId().equals(preferredOwnerId))
                .findFirst()
                .orElseGet(() -> remainingMembers.get(SECURE_RANDOM.nextInt(remainingMembers.size())));
        newOwner.changeRole(ChallengeMemberRole.OWNER);
    }

    @Transactional
    public GroupJoinResponse joinGroupByInviteCode(String inviteCode, Long userId) {
        ChallengeGroup group = challengeGroupRepository
                .findByInviteCodeWithLock(inviteCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND));
        if (group.getStatus() != GroupStatus.READY) {
            throw new BusinessException(ErrorCode.GROUP_NOT_JOINABLE);
        }
        if (group.getVisibility() != Visibility.CODE_ONLY) {
            throw new BusinessException(ErrorCode.GROUP_NOT_JOINABLE);
        }
        Challenge challenge = challengeRepository
                .findFirstByGroupIdAndStatus(group.getId(), ChallengeStatus.READY)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_JOINABLE));
        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new BusinessException(ErrorCode.ALREADY_JOINED);
        }
        long currentMembers = groupMemberRepository.countByGroupIdAndStatus(group.getId(), GroupMemberStatus.ACTIVE);
        if (currentMembers >= group.getMaxMembers()) {
            throw new BusinessException(ErrorCode.GROUP_FULL);
        }
        GroupMember groupMember =
                GroupMember.builder().group(group).userId(userId).build();
        GroupMember savedGroupMember = groupMemberRepository.save(groupMember);
        ChallengeMember challengeMember =
                challengeMemberService.createChallengeMember(challenge, userId, ChallengeMemberRole.MEMBER);
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new GroupJoinResponse(
                new GroupMemberResponse(savedGroupMember, user), challenge.getId(), challengeMember.getId());
    }

    @Transactional(readOnly = true)
    public InviteCodeResponse getInviteCode(Long groupId, Long userId) {
        ChallengeGroup group = challengeGroupRepository
                .findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));
        if (!group.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.GROUP_OWNER_ONLY);
        }
        if (group.getVisibility() != Visibility.CODE_ONLY) {
            throw new BusinessException(ErrorCode.INVITE_CODE_NOT_FOUND);
        }
        return new InviteCodeResponse(group.getInviteCode());
    }

    @Transactional(readOnly = true)
    public List<SeasonSummary> getGroupChallenges(Long groupId, Long userId) {
        challengeGroupRepository.findById(groupId).orElseThrow(() -> new BusinessException(ErrorCode.GROUP_NOT_FOUND));

        GroupMember groupMember = groupMemberRepository
                .findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_GROUP_MEMBER));

        if (groupMember.getStatus() != GroupMemberStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NOT_GROUP_MEMBER);
        }

        return challengeRepository.findAllByGroupIdOrderBySeqNoAsc(groupId).stream()
                .map(challenge -> new SeasonSummary(challenge.getId(), challenge.getSeqNo(), challenge.getStatus()))
                .toList();
    }
}

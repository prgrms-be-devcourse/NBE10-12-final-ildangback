package com.gommit.domain.group.dto.response;

import com.gommit.domain.group.entity.GroupMember;
import com.gommit.domain.group.entity.GroupMemberStatus;

import java.time.LocalDateTime;

public record GroupMemberResponse(
    Long id,
    Long groupId,
    Long userId,
    String nickname,
//    CharacterResponse character, // 아직 없어서 나중에 추가
    GroupMemberStatus status,
    LocalDateTime leftAt,
    LocalDateTime joinedAt
) {
    public GroupMemberResponse(
        GroupMember groupMember,
        String nickname
    ) {
        this(
            groupMember.getId(),
            groupMember.getGroup().getId(),
            groupMember.getUserId(),
            nickname,
            groupMember.getStatus(),
            groupMember.getLeftAt(),
            groupMember.getCreatedAt()
        );
    }
}

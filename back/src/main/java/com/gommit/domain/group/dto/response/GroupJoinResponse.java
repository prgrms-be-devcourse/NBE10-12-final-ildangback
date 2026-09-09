package com.gommit.domain.group.dto.response;

public record GroupJoinResponse(GroupMemberResponse groupMember, Long challengeId, Long challengeMemberId) {}

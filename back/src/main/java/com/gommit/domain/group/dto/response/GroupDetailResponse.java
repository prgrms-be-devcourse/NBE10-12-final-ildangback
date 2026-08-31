package com.gommit.domain.group.dto.response;

import com.gommit.domain.challenge.dto.response.ChallengeSummaryResponse;

import java.util.List;

public record GroupDetailResponse(
    GroupResponse group,
    ChallengeSummaryResponse currentChallenge,
    List<GroupMemberResponse> members
) {
    public GroupDetailResponse(
        GroupResponse group,
        ChallengeSummaryResponse currentChallenge,
        GroupMemberResponse member
    ) {
        this(
            group,
            currentChallenge,
            List.of(member)
        );
    }
}

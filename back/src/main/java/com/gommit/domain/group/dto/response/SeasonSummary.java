package com.gommit.domain.group.dto.response;

import com.gommit.domain.challenge.entity.ChallengeStatus;

public record SeasonSummary(
    Long id,
    int seqNo,
    ChallengeStatus status
) {
}

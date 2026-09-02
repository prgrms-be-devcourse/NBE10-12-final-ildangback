package com.gommit.domain.challenge.dto.response;

public record OwnerDelegationResponse(
    Long challengeId,
    Long previousOwnerId,
    Long newOwnerId
) {
}


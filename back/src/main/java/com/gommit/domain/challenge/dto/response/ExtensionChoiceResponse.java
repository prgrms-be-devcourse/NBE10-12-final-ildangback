package com.gommit.domain.challenge.dto.response;

import com.gommit.domain.challenge.entity.ExtensionChoice;

public record ExtensionChoiceResponse(
        Long challengeId, Long userId, ExtensionChoice choice, int pendingCount, int extendCount, int declineCount) {}

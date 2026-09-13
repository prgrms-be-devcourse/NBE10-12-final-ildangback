package com.gommit.domain.group.dto.response;

import com.gommit.domain.group.entity.KickVoteChoice;
import java.time.LocalDateTime;

public record KickVoteStatusResponse(
        Long groupId,
        Long targetOwnerId,
        boolean inProgress,
        int agreeCount,
        int disagreeCount,
        int eligibleVoters,
        KickVoteChoice myChoice,
        LocalDateTime startedAt,
        LocalDateTime expiresAt) {}

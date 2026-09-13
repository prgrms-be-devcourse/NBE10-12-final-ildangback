package com.gommit.domain.group.dto.request;

import com.gommit.domain.group.entity.KickVoteChoice;
import jakarta.validation.constraints.NotNull;

public record CastVoteRequest(
        @NotNull(message = "투표 선택(AGREE 또는 DISAGREE)은 필수 입니다.")
        KickVoteChoice choice) {}

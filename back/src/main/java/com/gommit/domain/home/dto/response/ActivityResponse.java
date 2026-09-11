package com.gommit.domain.home.dto.response;

import com.gommit.domain.point.entity.UserPointReason;
import java.time.LocalDateTime;

public record ActivityResponse(
        UserPointReason type, String commitPrefix, String title, int pointAmount, LocalDateTime occurredAt) {}

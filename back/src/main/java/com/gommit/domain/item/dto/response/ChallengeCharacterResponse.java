package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.Pose;
import java.util.Map;

public record ChallengeCharacterResponse(Long userId, String nickname, Pose pose, Map<ItemSlot, String> slots) {}

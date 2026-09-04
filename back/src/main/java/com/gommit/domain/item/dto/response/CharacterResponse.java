package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.CheckInState;
import com.gommit.domain.item.entity.ItemSlot;
import java.util.Map;

public record CharacterResponse(Map<ItemSlot, String> slots, CheckInState checkInState) {}

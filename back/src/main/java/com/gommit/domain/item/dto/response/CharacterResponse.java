package com.gommit.domain.item.dto.response;

import com.gommit.domain.item.entity.CheckInState;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class CharacterResponse {
    private Map<ItemSlot, String> slots;
    private CheckInState checkInState;


    public CharacterResponse(Map<ItemSlot, String> slots, CheckInState checkInState) {
        this.slots = slots;
        this.checkInState = checkInState;
    }

}

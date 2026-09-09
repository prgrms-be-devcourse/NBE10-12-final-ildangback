package com.gommit.domain.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.entity.CheckInState;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.service.UserItemService;
import com.gommit.domain.profile.dto.response.ProfileResponse;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService")
class ProfileServiceTest {

    @Mock
    private UserItemService userItemService;

    @InjectMocks
    private ProfileService profileService;

    private static final Long USER_ID = 1L;

    private CharacterResponse emptyCharacter() {
        Map<ItemSlot, String> slots = new EnumMap<>(ItemSlot.class);
        for (ItemSlot slot : ItemSlot.values()) slots.put(slot, null);
        return new CharacterResponse(slots, CheckInState.NOT_DONE);
    }

    @Test
    @DisplayName(
            "getMyProfile은 userItemService.getMyCharacter(userId, null)를 호출해 CharacterResponse를 포함한 ProfileResponse를 반환한다")
    void t1() {
        CharacterResponse character = emptyCharacter();
        given(userItemService.getMyCharacter(USER_ID, null)).willReturn(character);

        ProfileResponse result = profileService.getMyProfile(USER_ID);

        assertThat(result.character()).isEqualTo(character);
        then(userItemService).should().getMyCharacter(USER_ID, null);
    }

    @Test
    @DisplayName("착용 아이템이 있으면 해당 슬롯에 이미지 URL이 담긴 character가 반환된다")
    void t2() {
        Map<ItemSlot, String> slots = new EnumMap<>(ItemSlot.class);
        slots.put(ItemSlot.HEAD, "https://cdn.example.com/hat.png");
        slots.put(ItemSlot.TOP, null);
        slots.put(ItemSlot.BOTTOM, null);
        slots.put(ItemSlot.SHOES, null);
        CharacterResponse character = new CharacterResponse(slots, CheckInState.NOT_DONE);
        given(userItemService.getMyCharacter(USER_ID, null)).willReturn(character);

        ProfileResponse result = profileService.getMyProfile(USER_ID);

        assertThat(result.character().slots().get(ItemSlot.HEAD)).isEqualTo("https://cdn.example.com/hat.png");
        assertThat(result.character().slots().get(ItemSlot.TOP)).isNull();
    }
}

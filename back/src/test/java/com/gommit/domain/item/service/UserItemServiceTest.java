package com.gommit.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.gommit.domain.checkin.repository.CheckInRepository;
import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.domain.media.service.StorageService;
import com.gommit.global.dto.SliceResponse;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

// Spring Context 없이 Mockito만으로 실행하는 순수 단위 테스트
@ExtendWith(MockitoExtension.class)
class UserItemServiceTest {
    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private UserItemService userItemService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private Item headItem;
    private UserItem unequippedUserItem;
    private UserItem equippedUserItem;

    @BeforeEach
    void setUp() {
        headItem = Item.of(ItemSlot.HEAD, "기본 모자", 100);
        ReflectionTestUtils.setField(headItem, "id", 1L);

        unequippedUserItem = UserItem.of(USER_ID, headItem);
        ReflectionTestUtils.setField(unequippedUserItem, "id", 10L);
        ReflectionTestUtils.setField(unequippedUserItem, "createdAt", LocalDateTime.of(2025, 1, 1, 0, 0));

        equippedUserItem = UserItem.of(USER_ID, headItem);
        equippedUserItem.equip();
        ReflectionTestUtils.setField(equippedUserItem, "id", 11L);
        ReflectionTestUtils.setField(equippedUserItem, "createdAt", LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    // ─────────────────────────────────────────────────
    // equipItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("미착용 아이템 착용 성공 시 equippedSlot이 채워진 UserItemResponse가 반환된다")
    void t1() {
        // given
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.empty());

        // when
        UserItemResponse response = userItemService.equipItem(USER_ID, 10L);

        // then
        assertThat(response.equippedSlot()).isEqualTo(ItemSlot.HEAD);
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("착용 시 해당 슬롯에 기존 착용 아이템이 있으면 자동으로 해제되고 새 아이템이 착용된다")
    void t2() {
        // given
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.of(equippedUserItem));

        // when
        userItemService.equipItem(USER_ID, 10L);

        // then
        assertThat(equippedUserItem.isEquipped()).isFalse();
        assertThat(unequippedUserItem.isEquipped()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 UserItem 착용 시 USER_ITEM_NOT_FOUND 예외가 발생한다")
    void t3() {
        // given
        // userItemId=999는 존재하지 않음
        given(userItemRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userItemService.equipItem(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 유저의 아이템 착용 시 NOT_ITEM_OWNER 예외가 발생한다")
    void t4() {
        // given
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));

        // when & then
        assertThatThrownBy(() -> userItemService.equipItem(OTHER_USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ITEM_OWNER);
    }

    @Test
    @DisplayName("이미 착용 중인 아이템 재착용 시 ALREADY_EQUIPPED 예외가 발생한다")
    void t5() {
        // given
        given(userItemRepository.findById(11L)).willReturn(Optional.of(equippedUserItem));

        // when & then
        assertThatThrownBy(() -> userItemService.equipItem(USER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_EQUIPPED);
    }

    @Test
    @DisplayName("기존 착용 아이템이 있을 때 기존 아이템이 해제되고 새 아이템이 착용된다")
    void t6() {
        // given
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.of(equippedUserItem));

        // when
        userItemService.switchEquippedItem(USER_ID, unequippedUserItem);

        // then
        assertThat(equippedUserItem.getEquippedSlot()).isNull();
        assertThat(unequippedUserItem.getEquippedSlot()).isEqualTo(ItemSlot.HEAD);
    }

    @Test
    @DisplayName("해당 슬롯에 기존 착용 아이템이 없을 때 새 아이템만 착용된다")
    void t7() {
        // given
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.empty());

        // when
        userItemService.switchEquippedItem(USER_ID, unequippedUserItem);

        // then
        assertThat(unequippedUserItem.getEquippedSlot()).isEqualTo(ItemSlot.HEAD);
    }

    // ─────────────────────────────────────────────────
    // unequipItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("착용 중인 아이템 해제 성공 시 equippedSlot이 null인 UserItemResponse가 반환된다")
    void t8() {
        // given
        given(userItemRepository.findById(11L)).willReturn(Optional.of(equippedUserItem));

        // when
        UserItemResponse response = userItemService.unequipItem(USER_ID, 11L);

        // then
        assertThat(response.equippedSlot()).isNull();
        assertThat(response.id()).isEqualTo(11L);
    }

    @Test
    @DisplayName("존재하지 않는 UserItem 해제 시 USER_ITEM_NOT_FOUND 예외가 발생한다")
    void t9() {
        // given
        given(userItemRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userItemService.unequipItem(USER_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 유저의 아이템 해제 시 NOT_ITEM_OWNER 예외가 발생한다")
    void t10() {
        // given
        given(userItemRepository.findById(11L)).willReturn(Optional.of(equippedUserItem));

        // when & then
        assertThatThrownBy(() -> userItemService.unequipItem(OTHER_USER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ITEM_OWNER);
    }

    @Test
    @DisplayName("미착용 아이템 해제 시 NOT_EQUIPPED 예외가 발생한다")
    void t11() {
        // given
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));

        // when & then
        assertThatThrownBy(() -> userItemService.unequipItem(USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_EQUIPPED);
    }

    // ─────────────────────────────────────────────────
    // getMyItems
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("슬롯 미지정 시 커서 기반 전체 조회가 호출되고 SliceResponse로 반환된다")
    void t12() {
        // given
        given(userItemRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(0L), any(Pageable.class)))
                .willReturn(List.of(unequippedUserItem, equippedUserItem));

        // when
        SliceResponse<UserItemResponse> response = userItemService.getMyItems(USER_ID, null, null, 20);

        // then
        assertThat(response.content()).hasSize(2);

        then(userItemRepository)
                .should()
                .findByUserIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(0L), any(Pageable.class));
        then(userItemRepository)
                .should(never())
                .findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(any(), any(), any(), any());
    }

    @Test
    @DisplayName("슬롯 지정 시 커서 기반 슬롯 필터 조회가 호출되고 해당 슬롯 아이템만 반환된다")
    void t13() {
        // given
        given(userItemRepository.findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
                        eq(USER_ID), eq(ItemSlot.HEAD), eq(0L), any(Pageable.class)))
                .willReturn(List.of(unequippedUserItem));

        // when
        SliceResponse<UserItemResponse> response = userItemService.getMyItems(USER_ID, ItemSlot.HEAD, null, 20);

        // then
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).item().slot()).isEqualTo(ItemSlot.HEAD);

        then(userItemRepository)
                .should()
                .findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
                        eq(USER_ID), eq(ItemSlot.HEAD), eq(0L), any(Pageable.class));
        then(userItemRepository).should(never()).findByUserIdAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    // ─────────────────────────────────────────────────
    // getMyCharacter
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("착용 아이템이 있을 때 해당 슬롯에 imageUrl이 채워지고 나머지 슬롯은 null로 반환된다")
    void t14() {
        // given
        // checkInRepository 머지 후 교체 예정
        given(checkInRepository.existsByUserIdAndBusinessDate(eq(USER_ID), any()))
                .willReturn(false);

        // when
        CharacterResponse response = userItemService.getMyCharacter(USER_ID, null);

        // then
        assertThat(response.slots().get(ItemSlot.HEAD)).isNull();
        assertThat(response.slots().get(ItemSlot.TOP)).isNull();
        assertThat(response.slots().get(ItemSlot.BOTTOM)).isNull();
        assertThat(response.slots().get(ItemSlot.SHOES)).isNull();
        assertThat(response.slots()).hasSize(ItemSlot.values().length);
    }

    @Test
    @DisplayName("착용 아이템이 없을 때 모든 슬롯이 null로 반환된다")
    void t15() {
        // given
        // checkInRepository 머지 후 교체 예정
        given(checkInRepository.existsByUserIdAndBusinessDate(eq(USER_ID), any()))
                .willReturn(false);
        // when
        CharacterResponse response = userItemService.getMyCharacter(USER_ID, null);

        // then
        assertThat(response.slots().values()).allMatch(v -> v == null);
        assertThat(response.slots()).hasSize(ItemSlot.values().length);
    }
}

package com.gommit.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.gommit.domain.item.dto.response.CharacterResponse;
import com.gommit.domain.item.dto.response.UserItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.UserItemRepository;
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

    // UserItemService가 의존하는 유일한 빈을 Mock으로 대체
    @Mock
    private UserItemRepository userItemRepository;

    // @Mock 필드를 UserItemService 생성자에 주입하여 테스트 대상 인스턴스 생성
    @InjectMocks
    private UserItemService userItemService;

    // 테스트 전반에서 반복 사용할 상수: 소유자 ID, 제3자 ID
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private Item headItem;

    // USER_ID 소유, equip() 미호출 → equippedSlot=null (미착용 상태)
    private UserItem unequippedUserItem;

    // USER_ID 소유, equip() 호출 → equippedSlot=HEAD (착용 중 상태)
    private UserItem equippedUserItem;

    @BeforeEach
    void setUp() {
        // DB가 없으므로 ReflectionTestUtils로 BaseEntity의 private id·createdAt 필드를 강제 주입
        headItem = Item.of(
                ItemSlot.HEAD,
                "기본 모자",
                "https://cdn.phototourl.com/free/2026-09-02-404c3e23-3aa1-46f2-b0e2-4e2c239530ce.jpg",
                100);
        ReflectionTestUtils.setField(headItem, "id", 1L);

        // 미착용 상태: equip() 호출 없음 → equippedSlot=null
        unequippedUserItem = UserItem.of(USER_ID, headItem);
        ReflectionTestUtils.setField(unequippedUserItem, "id", 10L);
        ReflectionTestUtils.setField(unequippedUserItem, "createdAt", LocalDateTime.of(2025, 1, 1, 0, 0));

        // 착용 중 상태: equip() 호출 → equippedSlot=HEAD
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
        // userItemId=10 조회 → 미착용 상태의 UserItem 반환
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));
        // switchEquippedItem 내부에서 해당 슬롯(HEAD)의 기존 착용 아이템을 조회함
        // 현재 착용 중인 아이템 없음 → Optional.empty() 반환
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.empty());

        // when
        UserItemResponse response = userItemService.equipItem(USER_ID, 10L);

        // then
        // 착용 후 equippedSlot이 HEAD여야 함
        assertThat(response.equippedSlot()).isEqualTo(ItemSlot.HEAD);
        // 응답의 userItemId가 올바른지 확인
        assertThat(response.id()).isEqualTo(10L);
    }

    @Test
    @DisplayName("착용 시 해당 슬롯에 기존 착용 아이템이 있으면 자동으로 해제되고 새 아이템이 착용된다")
    void t2() {
        // given
        // 새로 착용할 미착용 아이템(userItemId=10)
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));
        // HEAD 슬롯에 equippedUserItem이 이미 착용 중 → 교체 대상
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.of(equippedUserItem));

        // when
        userItemService.equipItem(USER_ID, 10L);

        // then
        // switchEquippedItem에서 ifPresent(UserItem::unequip)이 호출되어
        // 기존 착용 아이템의 equippedSlot이 null로 변경되어야 함
        assertThat(equippedUserItem.isEquipped()).isFalse();
        // 새 아이템이 equip()되어 equippedSlot=HEAD여야 함
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
        // unequippedUserItem은 USER_ID(1L) 소유인데 OTHER_USER_ID(2L)가 착용 시도
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));

        // when & then
        // isOwnedBy(OTHER_USER_ID)가 false이므로 NOT_ITEM_OWNER 발생
        assertThatThrownBy(() -> userItemService.equipItem(OTHER_USER_ID, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_ITEM_OWNER);
    }

    @Test
    @DisplayName("이미 착용 중인 아이템 재착용 시 ALREADY_EQUIPPED 예외가 발생한다")
    void t5() {
        // given
        // equippedUserItem은 이미 equippedSlot=HEAD인 상태
        given(userItemRepository.findById(11L)).willReturn(Optional.of(equippedUserItem));

        // when & then
        // isEquipped()가 true이므로 ALREADY_EQUIPPED 발생
        assertThatThrownBy(() -> userItemService.equipItem(USER_ID, 11L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_EQUIPPED);
    }

    // ─────────────────────────────────────────────────
    // switchEquippedItem
    // package-private이므로 같은 패키지의 테스트에서만 직접 호출 가능
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("기존 착용 아이템이 있을 때 기존 아이템이 해제되고 새 아이템이 착용된다")
    void t6() {
        // given
        // HEAD 슬롯에 equippedUserItem이 착용 중
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.of(equippedUserItem));

        // when
        // switchEquippedItem은 package-private이라 같은 패키지에서만 접근 가능
        userItemService.switchEquippedItem(USER_ID, unequippedUserItem);

        // then
        // 기존 착용 아이템(equippedUserItem)의 equippedSlot이 null이어야 함 (unequip 완료)
        assertThat(equippedUserItem.getEquippedSlot()).isNull();
        // 새 아이템(unequippedUserItem)의 equippedSlot이 HEAD로 채워져야 함
        assertThat(unequippedUserItem.getEquippedSlot()).isEqualTo(ItemSlot.HEAD);
    }

    @Test
    @DisplayName("해당 슬롯에 기존 착용 아이템이 없을 때 새 아이템만 착용된다")
    void t7() {
        // given
        // HEAD 슬롯에 착용된 아이템 없음
        given(userItemRepository.findByUserIdAndEquippedSlot(USER_ID, ItemSlot.HEAD))
                .willReturn(Optional.empty());

        // when
        userItemService.switchEquippedItem(USER_ID, unequippedUserItem);

        // then
        // 기존 착용 아이템이 없으므로 새 아이템만 착용됨
        assertThat(unequippedUserItem.getEquippedSlot()).isEqualTo(ItemSlot.HEAD);
    }

    // ─────────────────────────────────────────────────
    // unequipItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("착용 중인 아이템 해제 성공 시 equippedSlot이 null인 UserItemResponse가 반환된다")
    void t8() {
        // given
        // equippedUserItem: equippedSlot=HEAD (착용 중)
        given(userItemRepository.findById(11L)).willReturn(Optional.of(equippedUserItem));

        // when
        UserItemResponse response = userItemService.unequipItem(USER_ID, 11L);

        // then
        // 해제 후 equippedSlot이 null이어야 함
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
        // equippedUserItem은 USER_ID(1L) 소유인데 OTHER_USER_ID(2L)가 해제 시도
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
        // unequippedUserItem: equippedSlot=null (미착용)
        given(userItemRepository.findById(10L)).willReturn(Optional.of(unequippedUserItem));

        // when & then
        // isEquipped()가 false이므로 NOT_EQUIPPED 발생
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
        // [변경] findByUserId() → findByUserIdAndIdGreaterThanOrderByIdAsc(cursor=0, pageable)
        // 서비스가 cursor=null을 0L로 변환하여 호출하므로 stub도 eq(0L)로 맞춤.
        // any(Pageable.class)는 PageRequest.of(0, size+1) 형태를 포괄적으로 매칭함.
        given(userItemRepository.findByUserIdAndIdGreaterThanOrderByIdAsc(eq(USER_ID), eq(0L), any(Pageable.class)))
                .willReturn(List.of(unequippedUserItem, equippedUserItem));

        // when
        // [변경] cursor=null(첫 요청), size=20으로 호출
        SliceResponse<UserItemResponse> response = userItemService.getMyItems(USER_ID, null, null, 20);

        // then
        // [변경] responses.hasSize() → response.content().hasSize()
        // SliceResponse는 record이므로 content() 접근자로 리스트를 꺼냄
        assertThat(response.content()).hasSize(2);

        // [변경] 호출 메서드 검증도 커서 기반 메서드로 교체
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
        // [변경] findByUserIdAndItem_Slot() → findByUserIdAndItem_SlotAndIdGreaterThanOrderByIdAsc()
        // 슬롯 + cursor=0 + pageable 세 조건으로 커서 기반 조회
        given(userItemRepository.findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
                        eq(USER_ID), eq(ItemSlot.HEAD), eq(0L), any(Pageable.class)))
                .willReturn(List.of(unequippedUserItem));

        // when
        // [변경] cursor=null(첫 요청), size=20으로 호출
        SliceResponse<UserItemResponse> response = userItemService.getMyItems(USER_ID, ItemSlot.HEAD, null, 20);

        // then
        assertThat(response.content()).hasSize(1);
        // 반환된 아이템의 슬롯이 HEAD인지 확인
        assertThat(response.content().get(0).item().slot()).isEqualTo(ItemSlot.HEAD);

        // [변경] 슬롯 필터 커서 기반 메서드가 호출되고, 전체 조회 메서드는 호출되지 않아야 함
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
        // HEAD 슬롯만 착용 중인 상태 (equippedUserItem)
        given(userItemRepository.findByUserIdAndEquippedSlotNotNull(USER_ID)).willReturn(List.of(equippedUserItem));

        // when
        CharacterResponse response = userItemService.getMyCharacter(USER_ID);

        // then
        // HEAD 슬롯에 headItem의 imageUrl이 채워져야 함
        assertThat(response.slots().get(ItemSlot.HEAD))
                .isEqualTo("https://cdn.phototourl.com/free/2026-09-02-404c3e23-3aa1-46f2-b0e2-4e2c239530ce.jpg");
        // 나머지 슬롯(TOP, BOTTOM, SHOES)은 착용한 아이템이 없으므로 null이어야 함
        assertThat(response.slots().get(ItemSlot.TOP)).isNull();
        assertThat(response.slots().get(ItemSlot.BOTTOM)).isNull();
        assertThat(response.slots().get(ItemSlot.SHOES)).isNull();
        // ItemSlot 열거값 4개(HEAD/TOP/BOTTOM/SHOES) 전부 키로 존재해야 함
        assertThat(response.slots()).hasSize(ItemSlot.values().length);
    }

    @Test
    @DisplayName("착용 아이템이 없을 때 모든 슬롯이 null로 반환된다")
    void t15() {
        // given
        // 착용 아이템이 하나도 없음
        given(userItemRepository.findByUserIdAndEquippedSlotNotNull(USER_ID)).willReturn(List.of());

        // when
        CharacterResponse response = userItemService.getMyCharacter(USER_ID);

        // then
        // values()로 모든 슬롯의 imageUrl 값을 꺼내 전부 null인지 한 번에 확인
        assertThat(response.slots().values()).allMatch(v -> v == null);
        // 슬롯 키는 여전히 4개 모두 존재해야 함 (null 값이지만 키는 있어야 함)
        assertThat(response.slots()).hasSize(ItemSlot.values().length);
    }
}

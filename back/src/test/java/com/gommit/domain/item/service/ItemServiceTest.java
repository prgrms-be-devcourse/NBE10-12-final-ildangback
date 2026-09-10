package com.gommit.domain.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.gommit.domain.item.dto.request.ItemCreateRequest;
import com.gommit.domain.item.dto.response.ItemPurchaseResponse;
import com.gommit.domain.item.dto.response.ItemResponse;
import com.gommit.domain.item.dto.response.ShopItemResponse;
import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.Pose;
import com.gommit.domain.item.entity.UserItem;
import com.gommit.domain.item.repository.ItemImageRepository;
import com.gommit.domain.item.repository.ItemRepository;
import com.gommit.domain.item.repository.UserItemRepository;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaValidator;
import com.gommit.domain.point.dto.response.PointBalanceResponse;
import com.gommit.domain.point.entity.UserPointReason;
import com.gommit.domain.point.service.PersonalPointService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
public class ItemServiceTest {
    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private UserItemService userItemService;

    @Mock
    private PersonalPointService pointService;

    @Mock
    private ItemImageRepository itemImageRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MediaValidator mediaValidator;

    @Mock
    private ItemWriter itemWriter;

    // 위 @Mock 필드들을 생성자 주입 방식으로 ItemService에 주입
    @InjectMocks
    private ItemService itemService;

    // 여러 테스트에서 공통으로 쓸 것
    private Item headItem;
    private Item topItem;
    private UserItem ownedAndEquipped; // 보유 하고 착용 중인 상태
    private UserItem ownedNotEquipped; // 보유 중이나 미착용 상태

    @BeforeEach
    void setUp() {
        // Item.of()로 엔티티를 생성하면 id null
        // 테스트에서는 DB가 없으므로 ReflectionTestUtils.setField로 BaseEntity의 private id 필드를 강제로 주입
        headItem = Item.of(ItemSlot.HEAD, "기본 모자", 100);
        ReflectionTestUtils.setField(headItem, "id", 1L);

        topItem = Item.of(ItemSlot.TOP, "기본 상의", 200);
        ReflectionTestUtils.setField(topItem, "id", 2L);

        // userId = 1 이 headItem을 보유하고 착용중인 UserItem (equippedSlot = HEAD)
        ownedAndEquipped = UserItem.of(1L, headItem);
        ownedAndEquipped.equip(); // equip() 호출 → equippedSlot 필드가 HEAD로 세팅됨
        ReflectionTestUtils.setField(ownedAndEquipped, "id", 10L);
        ReflectionTestUtils.setField(ownedAndEquipped, "createdAt", LocalDateTime.of(2025, 1, 1, 0, 0));

        // userId=1이 topItem을 보유하지만 착용하지 않은 UserItem (equippedSlot=null)
        ownedNotEquipped = UserItem.of(1L, topItem);
        // equip() 호출 없음 → equippedSlot이 null인 초기 상태 유지
        ReflectionTestUtils.setField(ownedNotEquipped, "id", 11L);
        ReflectionTestUtils.setField(ownedNotEquipped, "createdAt", LocalDateTime.of(2025, 1, 2, 0, 0));
    }

    // ─────────────────────────────────────────────────
    // getShopItems
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("슬롯 미지정 시 전체 아이템 조회, 보유·장착 상태가 응답에 정확히 반영된다")
    void t1() {
        // given
        // 서비스가 cursor=null을 0으로 변환하여 이 메서드를 호출하므로 stub도 동일하게 맞춤.
        // any(Pageable.class)는 PageRequest.of(0, size+1) 형태의 Pageable을 포괄적으로 매칭함.
        given(itemRepository.findByIdGreaterThanOrderByIdAsc(eq(0L), any(Pageable.class)))
                .willReturn(List.of(headItem, topItem));
        // userId=1의 보유 아이템 전체를 반환 (ownedMap 구성용, 커서 없이 전체 조회)
        given(userItemRepository.findByUserId(1L)).willReturn(List.of(ownedAndEquipped, ownedNotEquipped));

        // when
        SliceResponse<ShopItemResponse> response = itemService.getShopItems(1L, null, null, 20);

        // then
        assertThat(response.content()).hasSize(2);

        // 첫 번째 항목 = headItem: 보유하고 착용 중이므로 owned=true, equipped=true
        assertThat(response.content().get(0).owned()).isTrue();
        assertThat(response.content().get(0).equipped()).isTrue();

        // 두 번째 항목 = topItem: 보유하지만 미착용이므로 owned=true, equipped=false
        assertThat(response.content().get(1).owned()).isTrue();
        assertThat(response.content().get(1).equipped()).isFalse();

        then(itemRepository).should().findByIdGreaterThanOrderByIdAsc(eq(0L), any(Pageable.class));
        then(itemRepository).should(never()).findBySlotAndIdGreaterThanOrderByIdAsc(any(), any(), any());
    }

    // ─────────────────────────────────────────────────
    // purchaseItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("미보유 아이템 구매 시 UserItem이 저장되고 switchEquippedItem이 호출된다")
    void t2() {
        // given
        // itemId=1인 아이템 조회 성공
        given(itemRepository.findById(1L)).willReturn(Optional.of(headItem));
        // userId=1은 itemId=1을 아직 보유하지 않음
        given(userItemRepository.existsByUserIdAndItemId(1L, 1L)).willReturn(false);

        UserItem saved = UserItem.of(1L, headItem);
        ReflectionTestUtils.setField(saved, "id", 10L);
        ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2025, 6, 1, 0, 0));
        given(userItemRepository.saveAndFlush(any(UserItem.class))).willReturn(saved);

        // 차감 후 잔액 후 조회 stub - 임의의 숫자로 지정
        given(pointService.getMyBalance(1L)).willReturn(new PointBalanceResponse(900, 0, 0, 0));

        // when
        ItemPurchaseResponse response = itemService.purchaseItem(1L, 1L);

        // then
        // 반환된 응답의 userItemId, itemId가 올바른지 검증
        // 응답에 반영된 잔액도 stub한 값과 일치하는지 확인
        assertThat(response.userItemId()).isEqualTo(10L);
        assertThat(response.itemId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualTo(900);

        then(userItemRepository).should(times(1)).saveAndFlush(any(UserItem.class));
        // 구매 후 자동 착용을 위해 switchEquippedItem이 호출되었는지 검증
        then(userItemService).should(times(1)).switchEquippedItem(eq(1L), any(UserItem.class));
        // 포인트 차감이 정확한 인자로 호출됐는지 검증
        then(pointService).should(times(1)).deduct(eq(1L), eq(100), eq(UserPointReason.ITEM_PURCHASE), eq("기본 모자"));
    }

    @Test
    @DisplayName("포인트 부족 시 구매가 중단되고 UserItem이 저장되지 않는다")
    void t2fail() {
        // given
        given(itemRepository.findById(1L)).willReturn(Optional.of(headItem));
        given(userItemRepository.existsByUserIdAndItemId(1L, 1L)).willReturn(false);

        // deduct()는 void 메서드이므로 예외 stub은 willThrow(...).given(mock).method(...) 순서로 작성
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.POINT_INSUFFICIENT))
                .given(pointService)
                .deduct(eq(1L), eq(100), eq(UserPointReason.ITEM_PURCHASE), eq("기본 모자"));

        // when & then
        assertThatThrownBy(() -> itemService.purchaseItem(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.POINT_INSUFFICIENT);

        // 포인트 부족으로 실패했으므로 UserItem은 저장되지 않아야 함
        then(userItemRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("존재하지 않는 아이템 구매 시 ITEM_NOT_FOUND 예외가 발생한다")
    void t3() {
        // given
        // itemId=999는 DB에 없으므로 Optional.empty() 반환
        given(itemRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        // assertThatThrownBy: 람다 안 코드가 예외를 던지는지 검증하는 AssertJ API
        assertThatThrownBy(() -> itemService.purchaseItem(1L, 999L))
                .isInstanceOf(BusinessException.class) // 예외 타입 검증
                .extracting(e -> ((BusinessException) e).getErrorCode()) // 예외에서 errorCode 추출
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND); // 에러코드 검증

        // 아이템이 없으므로 save()는 절대 호출되지 않아야 함
        then(userItemRepository).should(never()).save(any());
    }

    // ─────────────────────────────────────────────────
    // createItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("아이템 생성 시 이미지 URL이 생성되고 Item이 저장된 후 ItemResponse가 반환된다")
    void t4() {
        // given
        MockMultipartFile imageFile = new MockMultipartFile("images", "hat.png", "image/png", "fake-bytes".getBytes());
        ItemCreateRequest request =
                new ItemCreateRequest(ItemSlot.HEAD, "새 모자", 150, List.of(Pose.DEFAULT), List.of(imageFile));
        given(storageService.store(any(), any())).willReturn(new StorageResult("items/test.png"));
        given(itemWriter.saveItemWithImages(any(), any()))
                .willReturn(new ItemResponse(3L, ItemSlot.HEAD, "새 모자", "https://cdn.example.com/test.png", 150));

        ItemResponse response = itemService.createItem(request);

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.name()).isEqualTo("새 모자");
        assertThat(response.slot()).isEqualTo(ItemSlot.HEAD);
        assertThat(response.price()).isEqualTo(150);

        then(storageService).should(times(1)).store(any(), any());
        then(itemWriter).should(times(1)).saveItemWithImages(eq(request), eq(List.of("items/test.png")));
    }

    // ─────────────────────────────────────────────────
    // deleteItem
    // ─────────────────────────────────────────────────

    @Test
    @DisplayName("보유자 없는 아이템 삭제 시 delete가 호출된다")
    void t5() {
        // given
        given(itemRepository.findById(1L)).willReturn(Optional.of(headItem));
        given(userItemRepository.existsByItemId(1L)).willReturn(false);
        given(itemImageRepository.findByItemId(1L)).willReturn(List.of());
        // when
        itemService.deleteItem(1L);
        // then
        then(itemRepository).should(times(1)).delete(headItem);
    }

    @Test
    @DisplayName("존재하지 않는 아이템 삭제 시 ITEM_NOT_FOUND 예외가 발생한다")
    void t6() {
        // given
        given(itemRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> itemService.deleteItem(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);

        // 아이템이 없으므로 delete() 호출 없어야 함
        then(itemRepository).should(never()).delete(any());
    }

    @Test
    @DisplayName("보유 중인 사용자가 있는 아이템 삭제 시 ITEM_IN_USE 예외가 발생한다")
    void t7() {
        // given
        given(itemRepository.findById(1L)).willReturn(Optional.of(headItem));
        // 이 아이템을 보유한 유저가 존재
        given(userItemRepository.existsByItemId(1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> itemService.deleteItem(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_IN_USE);

        // 보유자가 있으므로 delete() 호출 없어야 함
        then(itemRepository).should(never()).delete(any());
    }
}

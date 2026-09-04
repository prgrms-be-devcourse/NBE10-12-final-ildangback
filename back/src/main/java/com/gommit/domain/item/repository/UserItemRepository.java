package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    // equipItem, unequipItem에서 findById 호출 시 item을 JOIN해서 가져온다.
    // item 필드가 LAZY라 별도 선언 없으면 getItem() 호출마다 추가 쿼리가 발생함.
    @EntityGraph(attributePaths = "item")
    Optional<UserItem> findById(Long id);

    // 상점 조회용: 유저 보유 아이템 전체 (페이지네이션 없음)
    // ItemService.getShopItems에서 ownedMap 구성 시 사용.
    // 상점 커서는 Item 기준이므로 UserItem 전체를 한 번에 가져와 Map으로 비교함.
    @EntityGraph(attributePaths = "item")
    List<UserItem> findByUserId(Long userId);

    // getMyItems 전체 조회: cursor 이후 UserItem을 size+1개 가져옴.
    // @EntityGraph로 item을 즉시 로딩해 N+1 방지.
    @EntityGraph(attributePaths = "item")
    List<UserItem> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long cursor, Pageable pageable);

    // [변경] getMyItems 슬롯 필터 조회: slot + cursor 두 조건으로 size+1개 가져옴.
    @EntityGraph(attributePaths = "item")
    List<UserItem> findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
            Long userId, ItemSlot slot, Long cursor, Pageable pageable);

    // 구매 시 중복 보유 체크
    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    // 착용 교체: 해당 슬롯에 이미 착용 중인 아이템 단건 조회 (페이지네이션 불필요)
    Optional<UserItem> findByUserIdAndEquippedSlot(Long userId, ItemSlot slot);

    // 캐릭터 조회: 착용 중인 아이템 전체 (슬롯이 최대 4개라 전체 조회 부담 없음)
    @EntityGraph(attributePaths = "item")
    List<UserItem> findByUserIdAndEquippedSlotNotNull(Long userId);

    // 관리자 아이템 삭제 전 보유 유저 존재 여부 확인
    boolean existsByItemId(Long itemId);
}

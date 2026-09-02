package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {
    // 보유 아이템 전체
    List<UserItem> findByUserId(Long userId);
    // 보유 아이템 슬롯
    List<UserItem> findByUserIdAndItem_Slot(Long userId, ItemSlot slot);
    // 구매 시 중복 보유 체크
    boolean existsByUserIdAndItemId(Long userId, Long itemId);
    // 착용 교체
    Optional<UserItem> findByUserIdAndEquippedSlot(Long userId, ItemSlot slot);
    // 캐릭터 조회 - 장착 전체
    List<UserItem> findByUserIdAndEquippedSlotNotNull(Long userId);
    // 아이템 보유 확인
    boolean existsByItemId(Long itemId);
}

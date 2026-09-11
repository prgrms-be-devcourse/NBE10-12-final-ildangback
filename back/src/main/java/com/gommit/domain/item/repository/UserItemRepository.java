package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.ItemSlot;
import com.gommit.domain.item.entity.UserItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserItemRepository extends JpaRepository<UserItem, Long> {

    @EntityGraph(attributePaths = {"item", "item.images"})
    Optional<UserItem> findById(Long id);

    // 상점 조회용: 유저 보유 아이템 전체
    @EntityGraph(attributePaths = "item")
    List<UserItem> findByUserId(Long userId);

    // getMyItems 전체 조회
    @EntityGraph(attributePaths = {"item", "item.images"})
    List<UserItem> findByUserIdAndIdGreaterThanOrderByIdAsc(Long userId, Long cursor, Pageable pageable);

    // getMyItems 슬롯 필터 조회
    @EntityGraph(attributePaths = {"item", "item.images"})
    List<UserItem> findByUserIdAndItemSlotAndIdGreaterThanOrderByIdAsc(
            Long userId, ItemSlot slot, Long cursor, Pageable pageable);

    // 구매 시 중복 보유 체크
    boolean existsByUserIdAndItemId(Long userId, Long itemId);

    // 착용 교체: 해당 슬롯에 이미 착용 중인 아이템 단건 조회 (페이지네이션 불필요)
    Optional<UserItem> findByUserIdAndEquippedSlot(Long userId, ItemSlot slot);

    // 캐릭터 조회: 착용 중인 아이템 전체 (슬롯이 최대 4개라 전체 조회 부담 없음)
    @EntityGraph(attributePaths = {"item", "item.images"})
    List<UserItem> findByUserIdAndEquippedSlotNotNull(Long userId);

    // 챌린지 캐릭터 조회: 여러 유저의 착용 아이템을 한 번에
    @EntityGraph(attributePaths = {"item", "item.images"})
    List<UserItem> findByUserIdInAndEquippedSlotNotNull(Collection<Long> userIds);

    // 관리자 아이템 삭제 전 보유 유저 존재 여부 확인
    boolean existsByItemId(Long itemId);
}

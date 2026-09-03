package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {

    // cursor(마지막으로 받은 itemId)보다 큰 id를 오름차순으로 size+1개 가져온다.
    // 첫 요청은 cursor=0으로 호출하면 모든 아이템 범위가 됨.
    // Pageable은 PageRequest.of(0, size+1)로 넘겨 LIMIT 절을 만든다.
    List<Item> findByIdGreaterThanOrderByIdAsc(Long cursor, Pageable pageable);

    // slot + cursor 두 조건을 AND로 걸어 해당 슬롯의 cursor 이후 아이템만 가져온다.
    List<Item> findBySlotAndIdGreaterThanOrderByIdAsc(ItemSlot slot, Long cursor, Pageable pageable);
}

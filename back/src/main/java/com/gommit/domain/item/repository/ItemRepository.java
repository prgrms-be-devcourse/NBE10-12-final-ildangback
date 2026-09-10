package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findByIdGreaterThanOrderByIdAsc(Long cursor, Pageable pageable);

    List<Item> findBySlotAndIdGreaterThanOrderByIdAsc(ItemSlot slot, Long cursor, Pageable pageable);
}

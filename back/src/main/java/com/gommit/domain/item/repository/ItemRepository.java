package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.Item;
import com.gommit.domain.item.entity.ItemSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    List<Item> findBySlot(ItemSlot slot);
}

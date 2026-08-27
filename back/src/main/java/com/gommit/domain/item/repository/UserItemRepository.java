package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.UserItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserItemRepository extends JpaRepository<UserItem, Long> {}

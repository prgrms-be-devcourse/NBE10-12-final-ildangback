package com.gommit.domain.chat.repository;

import com.gommit.domain.chat.entity.GroupMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, Long> {

    @Query("""
        select m from GroupMessage m
         where m.groupId = :groupId
           and (:cursor is null or m.id < :cursor)
         order by m.id desc
        """)
    List<GroupMessage> findMessages(@Param("groupId") Long groupId, @Param("cursor") Long cursor, Pageable pageable);
}

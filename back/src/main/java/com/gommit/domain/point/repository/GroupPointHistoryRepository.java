package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.GroupPointHistory;
import com.gommit.domain.point.entity.GroupPointReason;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupPointHistoryRepository extends JpaRepository<GroupPointHistory, Long> {

    // 현재 잔액 = 가장 최근 이력의 balanceAfter
    Optional<GroupPointHistory> findTopByGroupIdOrderByIdDesc(Long groupId);

    // 커서 기반 목록 조회. earn=null(전체)/true(적립만)/false(차감만), reason=null이면 전체 사유
    @Query(
            "select h from GroupPointHistory h "
                    + "where h.groupId = :groupId "
                    + "and (:cursor is null or h.id < :cursor) "
                    + "and (:from is null or h.createdAt >= :from) "
                    + "and (:to is null or h.createdAt < :to) "
                    + "and (:reason is null or h.reason = :reason) "
                    + "and (:earn is null or (:earn = true and h.amount > 0) "
                    + "or (:earn = false and h.amount < 0)) "
                    + "order by h.id desc")
    List<GroupPointHistory> findHistories(
            @Param("groupId") Long groupId,
            @Param("cursor") Long cursor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("reason") GroupPointReason reason,
            @Param("earn") Boolean earn,
            Pageable pageable);
}

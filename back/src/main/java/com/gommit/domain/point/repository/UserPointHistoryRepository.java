package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointHistoryRepository extends JpaRepository<UserPointHistory, Long> {

    // 현재 잔액 = 가장 최근 이력의 balanceAfter
    Optional<UserPointHistory> findTopByUserIdOrderByIdDesc(Long userId);

    @Query(
            "select coalesce(sum(h.amount), 0) from UserPointHistory h "
                    + "where h.userId = :userId and h.amount > 0 and h.createdAt >= :from")
    int sumEarnedFrom(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query(
            "select coalesce(sum(-h.amount), 0) from UserPointHistory h "
                    + "where h.userId = :userId and h.amount < 0 and h.createdAt >= :from")
    int sumSpentFrom(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query(
            "select coalesce(sum(h.amount), 0) from UserPointHistory h "
                    + "where h.userId = :userId and h.amount > 0")
    int sumEarnedAll(@Param("userId") Long userId);

    // 커서 기반 목록 조회. earn=null(전체)/true(적립만)/false(차감만), reason=null이면 전체 사유
    @Query(
            "select h from UserPointHistory h "
                    + "where h.userId = :userId "
                    + "and (:cursor is null or h.id < :cursor) "
                    + "and (:from is null or h.createdAt >= :from) "
                    + "and (:to is null or h.createdAt < :to) "
                    + "and (:reason is null or h.reason = :reason) "
                    + "and (:earn is null or (:earn = true and h.amount > 0) "
                    + "or (:earn = false and h.amount < 0)) "
                    + "order by h.id desc")
    List<UserPointHistory> findHistories(
            @Param("userId") Long userId,
            @Param("cursor") Long cursor,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("reason") UserPointReason reason,
            @Param("earn") Boolean earn,
            Pageable pageable);
}

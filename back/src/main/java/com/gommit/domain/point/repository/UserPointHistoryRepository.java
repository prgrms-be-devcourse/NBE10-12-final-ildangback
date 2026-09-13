package com.gommit.domain.point.repository;

import com.gommit.domain.point.entity.UserPointHistory;
import com.gommit.domain.point.entity.UserPointReason;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserPointHistoryRepository extends JpaRepository<UserPointHistory, Long> {

    @Query("select coalesce(sum(h.amount), 0) from UserPointHistory h "
            + "where h.userId = :userId and h.amount > 0 and h.createdAt >= :from")
    int sumEarnedFrom(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("select coalesce(sum(-h.amount), 0) from UserPointHistory h "
            + "where h.userId = :userId and h.amount < 0 and h.createdAt >= :from")
    int sumSpentFrom(@Param("userId") Long userId, @Param("from") LocalDateTime from);

    @Query("select coalesce(sum(h.amount), 0) from UserPointHistory h " + "where h.userId = :userId and h.amount > 0")
    int sumEarnedAll(@Param("userId") Long userId);

    // 챌린지 중도 탈퇴 회수용 - 그 챌린지 활동으로 적립된(양수) 이력 합계. ChallengeMember가
    // ACTIVE -> LEFT/KICKED로 바뀌는 건 한 번뿐이라 recoverChallengePoints도 챌린지당 한 번만
    // 불린다 - 그래서 여기서 "이미 회수됐는지"는 따로 안 뺀다.
    @Query("select coalesce(sum(h.amount), 0) from UserPointHistory h "
            + "where h.userId = :userId and h.challengeId = :challengeId and h.amount > 0")
    int sumEarnedByUserIdAndChallengeId(@Param("userId") Long userId, @Param("challengeId") Long challengeId);

    // 커서 기반 목록 조회. earn=null(전체)/true(적립만)/false(차감만), reason=null이면 전체 사유
    @Query("select h from UserPointHistory h "
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

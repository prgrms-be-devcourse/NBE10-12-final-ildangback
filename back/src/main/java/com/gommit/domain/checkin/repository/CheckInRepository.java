package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    int countByChallengeIdAndUserIdAndBusinessDate(Long challengeId, Long userId, LocalDate businessDate);

    @Query("""
            select c.userId from CheckIn c
            where c.challengeId = :challengeId and c.businessDate = :businessDate
            group by c.userId
            having count(c) >= :target
            """)
    List<Long> findCompletedUserIds(
            @Param("challengeId") Long challengeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("target") int target);

    // 그룹 하루 전원 완료 판정용. userIds 중 businessDate 에 목표(target 회차)를 채운 유저.
    // 다른 멤버가 방금 커밋한 인증까지 보려면 새 트랜잭션(새 스냅샷)에서 호출해야 한다(GroupDailyCompletionReader).
    @Query("select c.userId from CheckIn c "
            + "where c.challengeId = :challengeId and c.businessDate = :businessDate and c.userId in :userIds "
            + "group by c.userId having count(c) >= :target")
    List<Long> findCompletedUserIdsAmong(
            @Param("challengeId") Long challengeId,
            @Param("businessDate") LocalDate businessDate,
            @Param("target") int target,
            @Param("userIds") Collection<Long> userIds);

    // 갤러리 — 그룹원 전체. from~to(월 범위) / userId / checkInType 필터, id 커서(내림차순).
    // maxBusinessDate: businessDate 상한 (null = 제한 없음). 이탈 멤버에게 이탈일 이하 기록만 보이게 할 때 쓴다.
    @Query("""
            select c from CheckIn c
            where c.challengeId = :challengeId
              and (:from is null or c.businessDate >= :from)
              and (:to is null or c.businessDate <= :to)
              and (:userId is null or c.userId = :userId)
              and (:checkInType is null or c.checkInType = :checkInType)
              and (:maxBusinessDate is null or c.businessDate <= :maxBusinessDate)
              and (:cursorId is null or c.id < :cursorId)
            order by c.id desc
            """)
    List<CheckIn> findGallery(
            @Param("challengeId") Long challengeId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("userId") Long userId,
            @Param("checkInType") CheckInType checkInType,
            @Param("maxBusinessDate") LocalDate maxBusinessDate,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    // 최근 인증 로그 한줄보기 — 갤러리의 축약. maxBusinessDate 규칙 동일. 커서 없음, 상위 N개만.
    @Query("""
            select c from CheckIn c
            where c.challengeId = :challengeId
              and (:maxBusinessDate is null or c.businessDate <= :maxBusinessDate)
            order by c.id desc
            """)
    List<CheckIn> findRecent(
            @Param("challengeId") Long challengeId, @Param("maxBusinessDate") LocalDate maxBusinessDate, Limit limit);

    // 내 인증 모아보기 — challengeId / checkInType / 기간(month) 필터, id 커서(내림차순).
    @Query("""
            select c from CheckIn c
            where c.userId = :userId
              and (:challengeId is null or c.challengeId = :challengeId)
              and (:checkInType is null or c.checkInType = :checkInType)
              and (:from is null or c.businessDate >= :from)
              and (:to is null or c.businessDate <= :to)
              and (:cursorId is null or c.id < :cursorId)
            order by c.id desc
            """)
    List<CheckIn> findMine(
            @Param("userId") Long userId,
            @Param("challengeId") Long challengeId,
            @Param("checkInType") CheckInType checkInType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    @Query("""
            select count(c) from CheckIn c
            where c.userId = :userId
              and (:challengeId is null or c.challengeId = :challengeId)
              and (:checkInType is null or c.checkInType = :checkInType)
              and (:from is null or c.businessDate >= :from)
              and (:to is null or c.businessDate <= :to)
            """)
    long countMine(
            @Param("userId") Long userId,
            @Param("challengeId") Long challengeId,
            @Param("checkInType") CheckInType checkInType,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            SELECT c.businessDate, COUNT(c)
            FROM CheckIn c
            WHERE c.userId = :userId
                AND c.businessDate BETWEEN :from AND :to
            GROUP BY c.businessDate
            """)
    List<Object[]> countByUserIdGroupByDateBetween(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
        SELECT COUNT(DISTINCT c.businessDate)
        FROM CheckIn c
        WHERE c.userId = :userId
            AND c.businessDate BETWEEN :from AND :to
        """)
    long countDistinctDatesByUserIdBetween(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}

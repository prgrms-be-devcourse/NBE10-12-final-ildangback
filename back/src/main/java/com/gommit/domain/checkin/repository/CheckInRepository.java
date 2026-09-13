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

    public interface CheckInCountByDate {
        LocalDate getBusinessDate();

        Long getCount();
    }

    public interface UserBusinessDate {
        Long getUserId();

        LocalDate getBusinessDate();
    }

    // 머지 생성 배치용 - 참여자 여러 명의 기간 내 인증 날짜를 한 번에 뽑는다(N+1 방지).
    // 참여자별로 distinct 처리는 호출부(RecordBatchService)에서 한다(하루 여러 번 인증 가능).
    @Query("""
            select c.userId as userId, c.businessDate as businessDate from CheckIn c
            where c.challengeId = :challengeId and c.userId in :userIds
              and c.businessDate between :from and :to
            """)
    List<UserBusinessDate> findBusinessDatesByChallengeIdAndUserIdInAndBusinessDateBetween(
            @Param("challengeId") Long challengeId,
            @Param("userIds") Collection<Long> userIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

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

    // 최근 인증 로그 한줄보기
    @Query("""
            select c from CheckIn c
            where c.challengeId = :challengeId
              and (:maxBusinessDate is null or c.businessDate <= :maxBusinessDate)
            order by c.id desc
            """)
    List<CheckIn> findRecent(
            @Param("challengeId") Long challengeId, @Param("maxBusinessDate") LocalDate maxBusinessDate, Limit limit);

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

    // DailyLog 몽타주 재료 — 그 날 인증 사진들을 회차 순서로.
    @Query("""
            select c from CheckIn c
            where c.challengeId = :challengeId and c.businessDate = :businessDate
            order by c.id asc
            """)
    List<CheckIn> findByChallengeIdAndBusinessDate(
            @Param("challengeId") Long challengeId, @Param("businessDate") LocalDate businessDate);

    @Query("""
            SELECT c.businessDate AS businessDate, COUNT(c) AS count
            FROM CheckIn c
            WHERE c.userId = :userId
                AND c.businessDate BETWEEN :from AND :to
            GROUP BY c.businessDate
            """)
    List<CheckInCountByDate> countByUserIdGroupByDateBetween(
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

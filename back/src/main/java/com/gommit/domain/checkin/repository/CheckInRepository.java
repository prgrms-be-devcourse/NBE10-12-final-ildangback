package com.gommit.domain.checkin.repository;

import com.gommit.domain.checkin.entity.CheckIn;
import com.gommit.domain.checkin.entity.CheckInType;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    int countByChallengeIdAndUserIdAndBusinessDate(Long challengeId, Long userId, LocalDate businessDate);

    // 갤러리 — 그룹원 전체. date / userId / checkInType 필터, id 커서(내림차순).
    // maxBusinessDate: businessDate 상한 (null = 제한 없음). 이탈 멤버에게 이탈일 이하 기록만 보이게 할 때 쓴다.
    @Query("""
            select c from CheckIn c
            where c.challengeId = :challengeId
              and (:date is null or c.businessDate = :date)
              and (:userId is null or c.userId = :userId)
              and (:checkInType is null or c.checkInType = :checkInType)
              and (:maxBusinessDate is null or c.businessDate <= :maxBusinessDate)
              and (:cursorId is null or c.id < :cursorId)
            order by c.id desc
            """)
    List<CheckIn> findGallery(
            @Param("challengeId") Long challengeId,
            @Param("date") LocalDate date,
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
}

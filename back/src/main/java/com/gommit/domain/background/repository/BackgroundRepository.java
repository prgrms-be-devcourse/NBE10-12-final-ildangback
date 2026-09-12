package com.gommit.domain.background.repository;

import com.gommit.domain.background.entity.Background;
import com.gommit.domain.group.entity.MapType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundRepository extends JpaRepository<Background, Long> {

    List<Background> findByMapTypeAndIdGreaterThanOrderByIdAsc(MapType mapType, Long cursor, Pageable pageable);

    @Query("""
        select b from Background b
         where b.mapType = :mapType
           and b.id > :cursor
           and exists (
               select 1 from GroupBackground gb
                where gb.groupId = :groupId and gb.background.id = b.id)
         order by b.id asc
        """)
    List<Background> findOwnedByGroupAndMapType(
            @Param("groupId") Long groupId,
            @Param("mapType") MapType mapType,
            @Param("cursor") Long cursor,
            Pageable pageable);

    @Query("""
        select b from Background b
         where b.mapType = :mapType
           and b.id > :cursor
           and not exists (
               select 1 from GroupBackground gb
                where gb.groupId = :groupId and gb.background.id = b.id)
         order by b.id asc
        """)
    List<Background> findNotOwnedByGroupAndMapType(
            @Param("groupId") Long groupId,
            @Param("mapType") MapType mapType,
            @Param("cursor") Long cursor,
            Pageable pageable);
}

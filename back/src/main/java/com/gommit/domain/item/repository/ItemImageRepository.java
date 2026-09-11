package com.gommit.domain.item.repository;

import com.gommit.domain.item.entity.ItemImage;
import com.gommit.domain.item.entity.Pose;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemImageRepository extends JpaRepository<ItemImage, Long> {
    // 아이템 목록의 DEFAULT 이미지를 한번에 조회 (N+1 방지용 배치 로드)
    @EntityGraph(attributePaths = "item")
    List<ItemImage> findByItemIdInAndPose(List<Long> itemIds, Pose pose);

    // 아이템 전체 이미지 조회(삭제용)
    List<ItemImage> findByItemId(Long itemId);
}

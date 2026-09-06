package com.gommit.domain.background.repository;

import com.gommit.domain.background.entity.BackgroundPurchaseVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BackgroundPurchaseVoteRepository extends JpaRepository<BackgroundPurchaseVote, Long> {

    List<BackgroundPurchaseVote> findAllByRequestId(Long requestId);

    boolean existsByRequestIdAndUserId(Long requestId, Long userId);
}

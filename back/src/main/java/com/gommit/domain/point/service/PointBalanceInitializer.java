package com.gommit.domain.point.service;

import com.gommit.domain.point.entity.GroupPoint;
import com.gommit.domain.point.entity.UserPoint;
import com.gommit.domain.point.repository.GroupPointRepository;
import com.gommit.domain.point.repository.UserPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 유저/그룹의 첫 포인트 잔액 행(0으로 시작)을 만든다.
// REQUIRES_NEW로 분리해서, 동시에 만들어져 유니크 제약 위반이 나도 호출한 쪽 트랜잭션은 안 깨지게 한다.
@Component
@RequiredArgsConstructor
public class PointBalanceInitializer {

    private final UserPointRepository userPointRepository;
    private final GroupPointRepository groupPointRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createUserPointIfAbsent(Long userId) {
        try {
            userPointRepository.save(UserPoint.init(userId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 트랜잭션이 먼저 만든 경우. 호출한 쪽에서 다시 조회하면 그 행을 찾는다.
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createGroupPointIfAbsent(Long groupId) {
        try {
            groupPointRepository.save(GroupPoint.init(groupId));
        } catch (DataIntegrityViolationException e) {
            // 동시에 다른 트랜잭션이 먼저 만든 경우. 호출한 쪽에서 다시 조회하면 그 행을 찾는다.
        }
    }
}

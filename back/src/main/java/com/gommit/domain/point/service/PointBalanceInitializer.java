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

/**
 * 유저/그룹의 첫 포인트 잔액 행(0으로 시작)을 만든다.
 *
 * <p>별도 트랜잭션(REQUIRES_NEW)으로 분리한 이유: 동시에 같은 유저의 첫 지급이 두 번 들어오면 유니크 제약(uk_user_points_user) 위반이 날 수
 * 있는데, 이걸 호출한 쪽의 트랜잭션 안에서 그대로 잡으면 그 트랜잭션 전체가 롤백 대상이 될 수 있다. 별도 트랜잭션으로 시도해서 실패해도 이 트랜잭션만 롤백되고, 호출한
 * 쪽은 "이미 누가 만들어놨다"고 보고 다시 조회하면 된다.
 */
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

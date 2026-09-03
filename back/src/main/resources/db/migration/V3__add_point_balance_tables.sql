-- 포인트 잔액 전용 테이블
-- user_point_histories/group_point_histories(이력)만으로 "현재 잔액"을 구하려면 매번
-- 최신 1건을 조회해야 하고, 동시에 지급/차감이 들어오면 그 최신 1건을 서로 다른 트랜잭션이
-- 동시에 읽어가서 잔액이 꼬일 수 있다(레이스 컨디션). 유저/그룹당 정확히 1행만 갖는 잔액
-- 테이블을 따로 두고, 이 행을 SELECT ... FOR UPDATE로 잠근 뒤 갱신해서 이 문제를 막는다.

-- ===== user_points : 개인 포인트 잔액 (유저당 1행) =====
CREATE TABLE user_points (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    balance    INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_points_user UNIQUE (user_id),
    CONSTRAINT fk_user_points_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ===== group_points : 그룹 포인트 잔액 (그룹당 1행) =====
CREATE TABLE group_points (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    balance    INT         NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_points_group UNIQUE (group_id),
    CONSTRAINT fk_group_points_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id)
);

-- findHistories 커서 조회(userId/groupId + id desc)용 인덱스.
-- 기존 (user_id, created_at)/(group_id, created_at) 인덱스는 기간 필터용으로 남겨둔다.
CREATE INDEX idx_user_point_histories_user_cursor ON user_point_histories (user_id, id);
CREATE INDEX idx_group_point_histories_group_cursor ON group_point_histories (group_id, id);

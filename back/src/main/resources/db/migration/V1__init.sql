-- 꼬밋(Go!mmit) 초기 스키마

-- ===== users : 계정 기본 정보 =====
CREATE TABLE users (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    email                VARCHAR(255) NOT NULL,
    password             VARCHAR(255) NOT NULL,
    nickname             VARCHAR(50)  NOT NULL,
    introduction         VARCHAR(255) NULL,
    role                 VARCHAR(20)  NOT NULL,
    personal_streak      INT          NOT NULL,
    best_streak          INT          NOT NULL,
    last_checked_in_date DATE         NULL,
    deleted_at           DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_nickname UNIQUE (nickname)
);

-- ===== refresh_tokens : 리프레시 토큰 =====
CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    revoked_at DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id, revoked_at);

-- ===== challenge_groups : 챌린지 그룹 =====
CREATE TABLE challenge_groups (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description TEXT         NULL,
    category    VARCHAR(20)  NOT NULL,
    map_type    VARCHAR(20)  NOT NULL,
    visibility  VARCHAR(20)  NOT NULL,
    max_members INT          NOT NULL,
    owner_id    BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_challenge_groups_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_groups_category_status ON challenge_groups (category, status);

-- ===== group_members : 그룹 회원 =====
CREATE TABLE group_members (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     VARCHAR(20) NOT NULL,
    left_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_members UNIQUE (group_id, user_id),
    CONSTRAINT fk_group_members_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id),
    CONSTRAINT fk_group_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_group_members_user ON group_members (user_id, status);

-- ===== challenges : 챌린지 한 시즌 =====
CREATE TABLE challenges (
    id                   BIGINT      NOT NULL AUTO_INCREMENT,
    group_id             BIGINT      NOT NULL,
    seq_no               INT         NOT NULL,
    start_date           DATE        NOT NULL,
    end_date             DATE        NOT NULL,
    status               VARCHAR(20) NOT NULL,
    frequency_type       VARCHAR(20) NOT NULL,
    frequency_value      INT         NULL,
    weekdays             VARCHAR(30) NULL,
    daily_check_in_count INT         NOT NULL,
    required_day_count   INT         NOT NULL,
    group_current_streak INT         NOT NULL,
    group_best_streak    INT         NOT NULL,
    allow_photo          BOOLEAN     NOT NULL,
    created_at           DATETIME(6) NOT NULL,
    updated_at           DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_challenges_group_seq UNIQUE (group_id, seq_no),
    CONSTRAINT fk_challenges_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id)
);

CREATE INDEX idx_challenges_status_end ON challenges (status, end_date);

-- ===== challenge_members : 챌린지 시즌별 개인 현재 통계 =====
CREATE TABLE challenge_members (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    challenge_id     BIGINT      NOT NULL,
    user_id          BIGINT      NOT NULL,
    role             VARCHAR(20) NOT NULL,
    status           VARCHAR(20) NOT NULL,
    current_streak   INT         NOT NULL,
    best_streak      INT         NOT NULL,
    left_at          DATETIME(6) NULL,
    extension_choice VARCHAR(20) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_challenge_members UNIQUE (challenge_id, user_id),
    CONSTRAINT fk_challenge_members_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
    CONSTRAINT fk_challenge_members_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_challenge_members_user ON challenge_members (user_id, status);

-- ===== check_ins : 개인의 인증 1건 =====
CREATE TABLE check_ins (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    challenge_id    BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    round_no        INT          NOT NULL,
    check_in_type   VARCHAR(20)  NOT NULL,
    media_url       VARCHAR(500) NOT NULL,
    media_type      VARCHAR(20)  NOT NULL,
    memo            VARCHAR(100) NULL,
    business_date   DATE         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_check_ins UNIQUE (challenge_id, user_id, business_date, round_no),
    CONSTRAINT fk_check_ins_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id),
    CONSTRAINT fk_check_ins_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_check_ins_daily ON check_ins (challenge_id, business_date);
CREATE INDEX idx_check_ins_user ON check_ins (user_id, business_date);

-- ===== daily_logs : 일일로그 =====
CREATE TABLE daily_logs (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    challenge_id BIGINT       NOT NULL,
    log_date     DATE         NOT NULL,
    video_url    VARCHAR(500) NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_logs UNIQUE (challenge_id, log_date),
    CONSTRAINT fk_daily_logs_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id)
);

-- ===== user_point_histories : 개인 포인트 변동 내역 =====
CREATE TABLE user_point_histories (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    challenge_id  BIGINT       NULL,
    source_name   VARCHAR(100) NOT NULL,
    amount        INT          NOT NULL,
    reason        VARCHAR(30)  NOT NULL,
    balance_after INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_point_histories_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_point_histories_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id)
);

CREATE INDEX idx_user_point_histories_user ON user_point_histories (user_id, created_at);

-- ===== group_point_histories : 그룹 포인트 변동 내역 =====
CREATE TABLE group_point_histories (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    group_id      BIGINT       NOT NULL,
    source_name   VARCHAR(100) NOT NULL,
    amount        INT          NOT NULL,
    reason        VARCHAR(30)  NOT NULL,
    balance_after INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_point_histories_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id)
);

CREATE INDEX idx_group_point_histories_group ON group_point_histories (group_id, created_at);

-- ===== items : 상점 판매 아이템 =====
CREATE TABLE items (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    slot       VARCHAR(20)  NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    image_url  VARCHAR(500) NOT NULL,
    price      INT          NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- ===== user_items : 유저 아이템 상태 =====
CREATE TABLE user_items (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    item_id        BIGINT      NOT NULL,
    equipped_slot  VARCHAR(20) NULL,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_items UNIQUE (user_id, item_id),
    CONSTRAINT uk_user_items_equipped UNIQUE (user_id, equipped_slot),
    CONSTRAINT fk_user_items_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_items_item FOREIGN KEY (item_id) REFERENCES items (id)
);

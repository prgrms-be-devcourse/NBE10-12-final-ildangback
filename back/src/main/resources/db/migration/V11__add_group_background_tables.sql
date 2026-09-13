-- ===== backgrounds : 상점에서 파는 배경 =====
CREATE TABLE backgrounds (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    map_type   VARCHAR(20)  NOT NULL,
    name       VARCHAR(50)  NOT NULL,
    image_key  VARCHAR(255) NOT NULL,
    price      INT          NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
);

-- ===== group_backgrounds : 그룹이 보유한 배경 =====
CREATE TABLE group_backgrounds (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    group_id      BIGINT      NOT NULL,
    background_id BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_backgrounds UNIQUE (group_id, background_id),
    CONSTRAINT fk_group_backgrounds_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id),
    CONSTRAINT fk_group_backgrounds_background FOREIGN KEY (background_id) REFERENCES backgrounds (id)
);

-- ===== background_purchase_requests : 배경 구매 제안 =====
CREATE TABLE background_purchase_requests (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    group_id      BIGINT      NOT NULL,
    background_id BIGINT      NOT NULL,
    requested_by  BIGINT      NOT NULL,
    status        VARCHAR(20) NOT NULL,
    expires_at    DATETIME(6) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_background_purchase_requests_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id),
    CONSTRAINT fk_background_purchase_requests_background FOREIGN KEY (background_id) REFERENCES backgrounds (id),
    CONSTRAINT fk_background_purchase_requests_user FOREIGN KEY (requested_by) REFERENCES users (id)
);

CREATE INDEX idx_background_purchase_requests ON background_purchase_requests (group_id, status);

-- ===== background_purchase_votes : 제안에 대한 그룹원 투표 =====
CREATE TABLE background_purchase_votes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    request_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    agreed     BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_background_purchase_votes UNIQUE (request_id, user_id),
    CONSTRAINT fk_background_purchase_votes_request FOREIGN KEY (request_id) REFERENCES background_purchase_requests (id),
    CONSTRAINT fk_background_purchase_votes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ===== reports : 신고 접수와 판정 =====
-- target_user_id 는 접수 시점에 확정한다. 대상이 사라져도 판정할 수 있어야 한다.
-- reported_content 는 접수 시점 원본 스냅샷이다.
CREATE TABLE reports (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    reporter_id      BIGINT        NOT NULL,
    target_type      VARCHAR(20)   NOT NULL,
    target_id        BIGINT        NOT NULL,
    target_user_id   BIGINT        NOT NULL,
    reason           VARCHAR(20)   NOT NULL,
    reported_content VARCHAR(1000) NULL,
    detail           VARCHAR(500)  NULL,
    status           VARCHAR(20)   NOT NULL,
    decided_by       BIGINT        NULL,
    decided_at       DATETIME(6)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_target_user FOREIGN KEY (target_user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_decider FOREIGN KEY (decided_by) REFERENCES users (id)
);

CREATE INDEX idx_reports_status ON reports (status, id);
CREATE INDEX idx_reports_reporter_target ON reports (reporter_id, target_type, target_id);
CREATE INDEX idx_reports_target ON reports (target_type, target_id);

-- ===== user_penalties : 판정 결과로 계정에 걸리는 제재 =====
-- ends_at 은 기간 정지만, amount 는 포인트 압수만 채운다.
-- revoked_at 은 이의제기가 인용되면 채워진다. 로그인 판정은 이 값이 NULL 인 것만 본다.
CREATE TABLE user_penalties (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    report_id    BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,
    penalty_type VARCHAR(20) NOT NULL,
    ends_at      DATETIME(6) NULL,
    amount       INT         NULL,
    revoked_at   DATETIME(6) NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_penalties_report FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_user_penalties_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_penalties_active ON user_penalties (user_id, revoked_at, ends_at);

-- ===== report_appeals : 신고 1건에 대한 이의제기 =====
CREATE TABLE report_appeals (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    report_id    BIGINT        NOT NULL,
    appellant_id BIGINT        NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    decided_by   BIGINT        NULL,
    decided_at   DATETIME(6)   NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_report_appeals_report UNIQUE (report_id),
    CONSTRAINT fk_report_appeals_report FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_report_appeals_appellant FOREIGN KEY (appellant_id) REFERENCES users (id),
    CONSTRAINT fk_report_appeals_decider FOREIGN KEY (decided_by) REFERENCES users (id)
);

CREATE INDEX idx_report_appeals_status ON report_appeals (status, id);

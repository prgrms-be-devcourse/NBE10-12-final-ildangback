-- ===== notifications : 알림 이력 =====
CREATE TABLE notifications (
                               id         BIGINT       NOT NULL AUTO_INCREMENT,
                               user_id    BIGINT       NOT NULL,
                               type       VARCHAR(30)  NOT NULL,
                               title      VARCHAR(100) NOT NULL,
                               body       VARCHAR(255) NULL,
                               ref_id     BIGINT       NULL,
                               read_at    DATETIME(6)  NULL,
                               created_at DATETIME(6)  NOT NULL,
                               updated_at DATETIME(6)  NOT NULL,

                               PRIMARY KEY (id),

                               CONSTRAINT fk_notifications_user
                                   FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_notifications_user
    ON notifications (user_id, read_at, created_at);

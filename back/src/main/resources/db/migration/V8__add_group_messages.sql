-- ===== group_messages : 그룹 채팅 메시지 =====
CREATE TABLE group_messages (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    group_id     BIGINT        NOT NULL,
    sender_id    BIGINT        NULL,
    message_type VARCHAR(20)   NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_group_messages_group FOREIGN KEY (group_id) REFERENCES challenge_groups (id),
    CONSTRAINT fk_group_messages_user FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE INDEX idx_group_messages_group_cursor ON group_messages (group_id, id);

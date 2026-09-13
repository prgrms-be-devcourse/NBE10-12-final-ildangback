-- 이메일 인증 / 소셜 로그인 / RT 폐기 사유 분리

-- ===== refresh_tokens : 폐기 사유 분리 =====
ALTER TABLE refresh_tokens ADD COLUMN rotated_at DATETIME(6) NULL;

-- ===== users : 이메일 인증 여부, 소셜 전용 가입자 =====
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users MODIFY COLUMN password VARCHAR(255) NULL;

-- 기존 계정은 검증 수단이 생기기 전에 가입했다.
UPDATE users SET email_verified = TRUE;

-- ===== auth_identities : 계정에 연결된 소셜 로그인 수단 =====
CREATE TABLE auth_identities (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_identities UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_auth_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_auth_identities_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_auth_identities_user ON auth_identities (user_id);

-- ===== email_tokens : 가입 인증, 비밀번호 재설정 =====
CREATE TABLE email_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    token_type VARCHAR(20)  NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_email_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_email_tokens_user ON email_tokens (user_id, token_type);

-- Record 도메인 : 월간/최종 머지
-- 한 번 발행되면 원본 데이터(체크인, 포인트 이력 등)가 바뀌어도 값이 안 바뀌는 스냅샷이다.
-- 월간 머지는 달력 월이 아니라 챌린지 시작일 기준 30일 롤링 주기로 발행된다.

-- ===== monthly_merges : 챌린지 진행 중 30일마다 발행되는 그룹 단위 체크포인트 =====
CREATE TABLE monthly_merges (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    challenge_id             BIGINT      NOT NULL,
    seq_no                   INT         NOT NULL,
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    total_days               INT         NOT NULL,
    total_check_in_count     INT         NOT NULL,
    average_completion_rate  INT         NOT NULL,
    published_at             DATETIME(6) NOT NULL,
    created_at               DATETIME(6) NOT NULL,
    updated_at               DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_monthly_merges_challenge_seq UNIQUE (challenge_id, seq_no),
    CONSTRAINT fk_monthly_merges_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id)
);

-- ===== monthly_merge_results : 월간 머지 1건에 대한 참여자별 결과 =====
-- completed_day_count: "며칠 인증했는지"(완료율의 분자). total_check_in_count는 하루
-- 여러 번 인증하면 기간 일수를 넘어갈 수 있어서 "N/기간일수" 표시엔 못 쓴다.
-- check_in_trend_*: "주간 인증 추이" 그래프용 콤마 구분 값("1주,2주,3주,4주" / "7,6,7,6").
-- CheckIn 도메인이 없어서 배치가 계산 못 하는 동안은 nullable - 값이 없으면 프론트가
-- 그래프 자체를 안 그린다.
CREATE TABLE monthly_merge_results (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    monthly_merge_id      BIGINT       NOT NULL,
    user_id               BIGINT       NOT NULL,
    ranking               INT          NOT NULL,
    completion_rate       INT          NOT NULL,
    completed_day_count   INT          NOT NULL,
    total_check_in_count  INT          NOT NULL,
    best_streak_in_period INT          NOT NULL,
    earned_points         INT          NOT NULL,
    contribution_rate     INT          NOT NULL,
    check_in_trend_labels VARCHAR(100) NULL,
    check_in_trend_counts VARCHAR(100) NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_monthly_merge_results UNIQUE (monthly_merge_id, user_id),
    CONSTRAINT fk_monthly_merge_results_merge FOREIGN KEY (monthly_merge_id) REFERENCES monthly_merges (id),
    CONSTRAINT fk_monthly_merge_results_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- ===== final_merges : 챌린지 종료 시 1회 발행되는 최종 결산 =====
CREATE TABLE final_merges (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    challenge_id             BIGINT      NOT NULL,
    period_start             DATE        NOT NULL,
    period_end               DATE        NOT NULL,
    total_days               INT         NOT NULL,
    total_check_in_count     INT         NOT NULL,
    average_completion_rate  INT         NOT NULL,
    published_at             DATETIME(6) NOT NULL,
    created_at               DATETIME(6) NOT NULL,
    updated_at               DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_final_merges_challenge UNIQUE (challenge_id),
    CONSTRAINT fk_final_merges_challenge FOREIGN KEY (challenge_id) REFERENCES challenges (id)
);

-- ===== final_merge_results : 최종 머지 1건에 대한 참여자별 결과 =====
-- completed_day_count/check_in_trend_* 의미는 monthly_merge_results와 동일하다
-- (기간이 챌린지 전체로 넓어졌을 뿐 - 그래프도 주간이 아니라 월별 추이가 된다).
CREATE TABLE final_merge_results (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    final_merge_id        BIGINT       NOT NULL,
    user_id               BIGINT       NOT NULL,
    ranking               INT          NOT NULL,
    completion_rate       INT          NOT NULL,
    completed_day_count   INT          NOT NULL,
    total_check_in_count  INT          NOT NULL,
    best_streak_in_period INT          NOT NULL,
    earned_points         INT          NOT NULL,
    contribution_rate     INT          NOT NULL,
    check_in_trend_labels VARCHAR(200) NULL,
    check_in_trend_counts VARCHAR(200) NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_final_merge_results UNIQUE (final_merge_id, user_id),
    CONSTRAINT fk_final_merge_results_merge FOREIGN KEY (final_merge_id) REFERENCES final_merges (id),
    CONSTRAINT fk_final_merge_results_user FOREIGN KEY (user_id) REFERENCES users (id)
);

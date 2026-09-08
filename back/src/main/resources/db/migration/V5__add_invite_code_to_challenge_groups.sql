-- 비공개 그룹 초대 코드
-- CODE_ONLY 그룹은 생성 시 6자리 초대 코드를 발급하고,
-- 최초 모집 상태(READY)에서만 해당 코드로 그룹에 참여할 수 있다.
-- PUBLIC 그룹은 초대 코드가 필요하지 않으므로 NULL을 허용한다.
-- 초대 코드는 하나의 그룹만 식별해야 하므로 UNIQUE 제약을 둔다.

ALTER TABLE challenge_groups
    ADD COLUMN invite_code VARCHAR(6);

ALTER TABLE challenge_groups
    ADD CONSTRAINT uk_challenge_groups_invite_code
        UNIQUE (invite_code);

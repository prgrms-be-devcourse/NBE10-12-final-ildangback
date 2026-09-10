-- 스트릭 연속성 판정을 위한 "마지막으로 하루 인증 목표를 모두 채운 날짜"(businessDate)
-- challenge_members.last_completed_date : 개인이 그 시즌에서 마지막으로 당일 목표를 채운 날
-- challenges.group_last_completed_date  : ACTIVE 멤버 전원이 마지막으로 당일 목표를 채운 날
-- users 는 이미 last_checked_in_date / personal_streak / best_streak 를 가진다.

ALTER TABLE challenge_members
    ADD COLUMN last_completed_date DATE NULL;

ALTER TABLE challenges
    ADD COLUMN group_last_completed_date DATE NULL;

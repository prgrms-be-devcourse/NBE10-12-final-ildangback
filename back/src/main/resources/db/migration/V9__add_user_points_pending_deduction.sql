-- 챌린지 중도 탈퇴 시 그 챌린지에서 번 포인트를 회수하는데, 이미 다 써버려서
-- 잔액이 모자라면 0까지만 깎고 나머지는 여기 쌓아둔다. 다음에 포인트가 들어올 때
-- (reward) 잔액에 반영되기 전에 이 빚부터 갚는다.
ALTER TABLE user_points ADD COLUMN pending_deduction INT NOT NULL DEFAULT 0;

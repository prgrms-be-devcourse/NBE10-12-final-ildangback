-- 개인(멤버) 스트릭을 저장값 없이 check_ins 에서 유도하도록 바꾸면서(businessDate 별 회차 수
-- >= daily_check_in_count 인 날), 쓰이지 않게 된 challenge_members 의 스트릭 컬럼을 모두 제거한다.
--   - last_completed_date : V6 에서 연속성 판정용으로 추가했으나 유도 방식에선 불필요
--   - current_streak / best_streak : V1 부터 있었으나 읽는 곳이 없던 미사용 컬럼
-- 그룹 스트릭 컬럼(challenges.group_*)은 그대로 유지한다.
-- H2 가 DROP COLUMN 콤마 문법을 지원하지 않아 문장을 분리한다.

ALTER TABLE challenge_members DROP COLUMN last_completed_date;
ALTER TABLE challenge_members DROP COLUMN current_streak;
ALTER TABLE challenge_members DROP COLUMN best_streak;

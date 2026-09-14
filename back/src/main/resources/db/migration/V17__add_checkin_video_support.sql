-- 영상 체크인 지원.
-- challenges.allow_video : allow_photo 와 같은 방식(boolean). LIVE 는 아직 없어서 Set 으로 미리
--   일반화하지 않음 — LIVE 가 실제로 생기면 그때 allow_live 컬럼을 추가한다.
-- check_ins.poster_key : 영상 체크인의 그리드 표시용 썸네일(ffmpeg 로 뽑은 정지 프레임) storage key. 이미지 체크인은 NULL.

ALTER TABLE challenges
    ADD COLUMN allow_video BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE check_ins
    ADD COLUMN poster_key VARCHAR(255) NULL;

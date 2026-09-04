-- media_url / image_url / video_url: URL 전체 대신 "스토리지 키" 저장으로 전환
--   - 로컬: base-dir 기준 상대경로 (예: check-ins/2026/09/{uuid}.jpg)
--   - Cloudinary: "{publicId}.{format}"
-- 조회 시 접근 방식
--   - PUBLIC(item): base-url + key 로 URL 조립
--   - PRIVATE(check-in, daily-log): 앱 서빙 엔드포인트 경유

ALTER TABLE check_ins  RENAME COLUMN media_url TO media_key;
ALTER TABLE items      RENAME COLUMN image_url TO image_key;
ALTER TABLE daily_logs RENAME COLUMN video_url TO video_key;

ALTER TABLE check_ins  MODIFY COLUMN media_key VARCHAR(255) NOT NULL;
ALTER TABLE items      MODIFY COLUMN image_key VARCHAR(255) NOT NULL;
ALTER TABLE daily_logs MODIFY COLUMN video_key VARCHAR(255);

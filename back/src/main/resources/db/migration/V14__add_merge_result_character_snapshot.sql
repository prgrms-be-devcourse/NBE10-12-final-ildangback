-- 머지 발행 시점 캐릭터 스냅샷. 이후 유저가 아이템을 바꿔도 이 머지 카드는 안 바뀐다.
ALTER TABLE monthly_merge_results ADD COLUMN head_image_url VARCHAR(255) NULL;
ALTER TABLE monthly_merge_results ADD COLUMN top_image_url VARCHAR(255) NULL;
ALTER TABLE monthly_merge_results ADD COLUMN bottom_image_url VARCHAR(255) NULL;
ALTER TABLE monthly_merge_results ADD COLUMN shoes_image_url VARCHAR(255) NULL;

ALTER TABLE final_merge_results ADD COLUMN head_image_url VARCHAR(255) NULL;
ALTER TABLE final_merge_results ADD COLUMN top_image_url VARCHAR(255) NULL;
ALTER TABLE final_merge_results ADD COLUMN bottom_image_url VARCHAR(255) NULL;
ALTER TABLE final_merge_results ADD COLUMN shoes_image_url VARCHAR(255) NULL;

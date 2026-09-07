CREATE TABLE item_images (
     id         BIGINT       NOT NULL AUTO_INCREMENT,
     item_id    BIGINT       NOT NULL,
     pose       VARCHAR(20)  NOT NULL,
     image_key  VARCHAR(255) NOT NULL,
     created_at DATETIME(6)  NOT NULL,
     updated_at DATETIME(6)  NOT NULL,
     PRIMARY KEY (id),
     CONSTRAINT uk_item_images_item_pose UNIQUE (item_id, pose),
     CONSTRAINT fk_item_images_item FOREIGN KEY (item_id) REFERENCES items (id)
);

ALTER TABLE items DROP COLUMN image_key;

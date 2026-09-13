package com.gommit.domain.checkin.media;

import org.springframework.core.io.Resource;

// 서버가 생성한 영상이라 검증(validate)이나 업로드용 store(MultipartFile) 는 없다.
public interface DailyLogMediaStore {
    // ffmpeg 로 만든 mp4 바이트를 저장하고 스토리지 키를 돌려준다.
    String store(byte[] video);

    void delete(String storageKey);

    Resource load(String storageKey);
}

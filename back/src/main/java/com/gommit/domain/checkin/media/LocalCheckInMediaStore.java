package com.gommit.domain.checkin.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// TODO(commit): 로컬 파일시스템 저장/조회 구현. 지금은 스켈레톤.
@Component
public class LocalCheckInMediaStore implements CheckInMediaStore {

    public LocalCheckInMediaStore(@Value("${checkin.media.base-dir:./data/media}") String baseDir) {
        // 본문 구현 커밋에서 baseDir 를 사용한다.
    }

    @Override
    public String reserve(MultipartFile file) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void write(MultipartFile file, String storageKey) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Resource load(String storageKey) {
        throw new UnsupportedOperationException("not implemented");
    }
}

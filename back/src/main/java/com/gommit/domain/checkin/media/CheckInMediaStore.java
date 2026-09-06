package com.gommit.domain.checkin.media;

import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 스켈레톤 — 구현은 "feat: 인증 서비스 + 정책 + 접근 판정" 커밋에서 채운다.
@Component
@RequiredArgsConstructor
public class CheckInMediaStore {

    private final StorageService storageService;
    private final MediaValidator mediaValidator;

    public String store(MultipartFile file) {
        throw new UnsupportedOperationException("미구현");
    }

    public void delete(String storageKey) {
        throw new UnsupportedOperationException("미구현");
    }

    public Resource load(String storageKey) {
        throw new UnsupportedOperationException("미구현");
    }
}

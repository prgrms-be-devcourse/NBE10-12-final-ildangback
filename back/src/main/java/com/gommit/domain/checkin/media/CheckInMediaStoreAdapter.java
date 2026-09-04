package com.gommit.domain.checkin.media;

import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// media(#8) `StorageService` 위임 어댑터. `MediaRole.CHECKIN` 고정 + "검증 후 저장" 을 묶는다.
// 정책(폴더 check-ins / 5MB / image png·jpeg·webp / PRIVATE)은 media.storage.policies.CHECKIN 이 소유한다.
@Component
@RequiredArgsConstructor
public class CheckInMediaStoreAdapter implements CheckInMediaStore {

    private final StorageService storageService;
    private final MediaValidator mediaValidator;

    @Override
    public String store(MultipartFile file) {
        mediaValidator.validate(file, MediaRole.CHECKIN); // 용량·타입·매직바이트
        return storageService.store(file, MediaRole.CHECKIN).storageKey();
    }

    @Override
    public void delete(String storageKey) {
        storageService.delete(storageKey, MediaRole.CHECKIN);
    }

    @Override
    public Resource load(String storageKey) {
        return storageService.load(storageKey, MediaRole.CHECKIN);
    }
}

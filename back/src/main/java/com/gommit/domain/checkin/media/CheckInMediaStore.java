package com.gommit.domain.checkin.media;

import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class CheckInMediaStore {

    private final StorageService storageService;
    private final MediaValidator mediaValidator;

    public String store(MultipartFile file) {
        mediaValidator.validate(file, MediaRole.CHECKIN); // 용량·타입·매직바이트
        return storageService.store(file, MediaRole.CHECKIN).storageKey();
    }

    public void delete(String storageKey) {
        storageService.delete(storageKey, MediaRole.CHECKIN);
    }

    public Resource load(String storageKey) {
        return storageService.load(storageKey, MediaRole.CHECKIN);
    }
}

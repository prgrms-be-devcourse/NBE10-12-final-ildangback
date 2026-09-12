package com.gommit.domain.checkin.media;

import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyLogMediaStoreAdapter implements DailyLogMediaStore {

    private final StorageService storageService;

    @Override
    public String store(byte[] video) {
        return storageService
                .storeGenerated(video, MediaContentType.MP4, MediaRole.DAILYLOG)
                .storageKey();
    }

    @Override
    public void delete(String storageKey) {
        storageService.delete(storageKey, MediaRole.DAILYLOG);
    }

    @Override
    public Resource load(String storageKey) {
        return storageService.load(storageKey, MediaRole.DAILYLOG);
    }
}

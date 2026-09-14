package com.gommit.domain.checkin.media;

import com.gommit.domain.checkin.entity.MediaType;
import com.gommit.domain.checkin.support.CheckInVideoTranscoder;
import com.gommit.domain.checkin.support.CheckInVideoTranscoder.Transcoded;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.service.StorageService;
import com.gommit.domain.media.support.MediaContentType;
import com.gommit.domain.media.support.MediaValidator;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckInMediaStore {

    private final StorageService storageService;
    private final MediaValidator mediaValidator;
    private final CheckInVideoTranscoder videoTranscoder;

    public UploadedMedia store(MultipartFile file) {
        mediaValidator.validate(file, MediaRole.CHECKIN); // 용량·타입·매직바이트
        MediaContentType contentType = MediaContentType.fromMimeType(file.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        if (!contentType.isVideo()) {
            String storageKey = storageService.store(file, MediaRole.CHECKIN).storageKey();
            return new UploadedMedia(storageKey, MediaType.IMAGE, null);
        }

        Transcoded transcoded = videoTranscoder.transcode(file, contentType.extension());
        String storageKey = storageService
                .storeGenerated(transcoded.video(), MediaContentType.MP4, MediaRole.CHECKIN)
                .storageKey();
        try {
            String posterKey = storageService
                    .storeGenerated(transcoded.poster(), MediaContentType.JPEG, MediaRole.CHECKIN)
                    .storageKey();
            return new UploadedMedia(storageKey, MediaType.VIDEO, posterKey);
        } catch (RuntimeException e) {
            log.warn("포스터 저장 실패 — 방금 올린 영상 orphan 정리: {}", storageKey, e);
            deleteQuietly(storageKey);
            throw e;
        }
    }

    public void delete(String storageKey) {
        delete(storageKey, null);
    }

    public void delete(String storageKey, String posterKey) {
        storageService.delete(storageKey, MediaRole.CHECKIN);
        if (posterKey != null) {
            deleteQuietly(posterKey);
        }
    }

    private void deleteQuietly(String storageKey) {
        try {
            storageService.delete(storageKey, MediaRole.CHECKIN);
        } catch (RuntimeException e) {
            log.warn("orphan 미디어 정리 실패: {}", storageKey, e);
        }
    }

    public Resource load(String storageKey) {
        return storageService.load(storageKey, MediaRole.CHECKIN);
    }
}

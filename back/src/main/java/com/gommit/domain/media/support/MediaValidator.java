package com.gommit.domain.media.support;

import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 업로드 파일이 StoragePolicy 를 만족하는지 검증. 용량, 타입 확인.
@Component
public class MediaValidator {

    private static final int SIGNATURE_BYTES = 12;

    private final MediaStorageProperties properties;

    public MediaValidator(MediaStorageProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file, MediaRole role) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }

        StoragePolicy policy = properties.policyFor(role);

        if (file.getSize() > policy.maxSize().toBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String declaredType = file.getContentType();
        if (declaredType == null || !policy.allowsContentType(declaredType)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        MediaContentType contentType = MediaContentType.fromMimeType(declaredType)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        if (!contentType.matchesSignature(readHeader(file))) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }
}

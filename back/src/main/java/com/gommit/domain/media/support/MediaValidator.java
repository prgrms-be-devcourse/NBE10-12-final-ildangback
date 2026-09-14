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

        // MediaContentType.fromMimeType 이 코덱 파라미터("video/webm;codecs=vp9")를 떼어내 정규화하므로,
        // 정책 허용 여부도 선언값 그대로가 아니라 정규화된 mimeType() 으로 비교한다.
        MediaContentType contentType = MediaContentType.fromMimeType(file.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        if (!policy.allowsContentType(contentType.mimeType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

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

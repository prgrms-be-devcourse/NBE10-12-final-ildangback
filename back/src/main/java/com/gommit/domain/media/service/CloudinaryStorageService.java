package com.gommit.domain.media.service;

import com.gommit.domain.media.dto.FileStoreResult;
import com.gommit.domain.media.entity.MediaRole;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// Cloudinary 스토리지 구현 (미완성 스텁).
// media.storage.provider=cloudinary 로 전환할 때 Cloudinary SDK 의존성과 함께 채운다.
// provider=cloudinary 면 빈으로는 등록되지만 모든 메서드가 호출 시 예외를 던진다 (MediaConfig 참고).
public class CloudinaryStorageService implements StorageService {

    private static final String NOT_IMPLEMENTED =
            "CloudinaryStorageService 는 아직 구현되지 않았습니다. media.storage.provider=local 을 사용하세요.";

    @Override
    public FileStoreResult store(MultipartFile file, MediaRole mediaRole) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public Resource load(String storageKey, MediaRole mediaRole) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public void delete(String storageKey, MediaRole mediaRole) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public String publicUrl(String storageKey, MediaRole mediaRole) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }
}

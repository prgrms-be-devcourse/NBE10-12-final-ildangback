package com.gommit.domain.media.service;

import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// key 기반 메서드가 MediaRole 을 함께 받는 이유: Cloudinary 는 리소스를 다루려면
// resource_type(이미지/영상)과 type(공개/비공개)이 필요한데, publicId 만으로 알 수 없음.
// type 은 role -> StoragePolicy.visibility 로, resource_type 은 storageKey 의 확장자로 결정.
public interface StorageService {
    StorageResult store(MultipartFile file, MediaRole mediaRole);

    Resource load(String storageKey, MediaRole mediaRole);

    void delete(String storageKey, MediaRole mediaRole);

    // PUBLIC 에만 사용. PRIVATE 미디어가 호출시 찾을 수 없어 404 반환. PRIVATE에는 load()사용.
    String publicUrl(String storageKey);
}

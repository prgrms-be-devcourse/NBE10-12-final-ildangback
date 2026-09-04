package com.gommit.domain.checkin.media;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// 인증 미디어 저장/조회 — media(#8) `StorageService` 로의 얇은 어댑터 경계.
// 항상 `MediaRole.CHECKIN` 이라 role 을 노출하지 않고, "검증 후 저장" 을 한 메서드로 묶는다.
// provider(local/cloudinary) 전환은 `StorageService` 가 처리하므로 checkin 은 신경 쓰지 않는다.
public interface CheckInMediaStore {

    // 파일을 검증(EMPTY_FILE / FILE_TOO_LARGE / UNSUPPORTED_MEDIA_TYPE)하고 저장한 뒤 스토리지 키를 돌려준다.
    String store(MultipartFile file);

    // 저장 후 인증 row 저장이 실패했을 때 방금 쓴 파일을 정리한다.
    void delete(String storageKey);

    // 스토리지 키로 저장된 바이트를 읽는다. 없으면 MEDIA_NOT_FOUND, 경로 탈출은 ACCESS_DENIED.
    Resource load(String storageKey);
}

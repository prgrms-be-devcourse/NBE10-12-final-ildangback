package com.gommit.domain.media.config;

import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

// application.yml 의 media.storage.* 를 바인딩하는 설정 객체.
//   provider   : 사용할 스토리지 구현 선택 ("local" 또는 "cloudinary")
//   policies   : 예시 - policies.ITEM = { folder: character-store, max-size: 5MB, visibility: PUBLIC, ... }
@ConfigurationProperties("media.storage")
public record MediaStorageProperties(
        String provider, LocalPaths local, CloudinaryAccount cloudinary, Map<MediaRole, StoragePolicy> policies) {

    // baseDir : 파일시스템 루트
    // baseUrl : PUBLIC 미디어 공개 URL 접두사. publicUrl(key) = baseUrl + "/" + storageKey.
    //           MediaLocalResourceConfig 의 정적 서빙 경로(baseUrl 의 path)와 일치해야 한다.
    public record LocalPaths(String baseDir, String baseUrl) {}

    // rootFolder : Cloudinary 계정 안에서 이 서비스가 쓰는 최상위 폴더(예: go-mmit / test-go-mmit).
    //   실제 저장 폴더 = rootFolder + "/" + StoragePolicy.folder. (비어 있으면 policy.folder 그대로)
    public record CloudinaryAccount(String cloudName, String apiKey, String apiSecret, String rootFolder) {}

    public StoragePolicy policyFor(MediaRole role) {
        StoragePolicy policy = policies.get(role);
        if (policy == null) {
            throw new IllegalStateException("설정된 StoragePolicy 가 없습니다: media.storage.policies." + role);
        }
        return policy;
    }
}

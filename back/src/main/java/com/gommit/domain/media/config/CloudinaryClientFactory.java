package com.gommit.domain.media.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gommit.domain.media.config.MediaStorageProperties.CloudinaryAccount;

// media.storage.cloudinary.* 설정으로 Cloudinary 클라이언트를 만든다.
// MediaConfig 가 provider=cloudinary 일 때 호출됨
public final class CloudinaryClientFactory {

    // http5 네트워크 타임아웃. 40MB 영상 전송 고려해 60초.
    private static final int HTTP_TIMEOUT_SECONDS = 60;

    private CloudinaryClientFactory() {}

    public static Cloudinary create(CloudinaryAccount account) {
        if (account == null
                || isBlank(account.cloudName())
                || isBlank(account.apiKey())
                || isBlank(account.apiSecret())) {
            throw new IllegalStateException("media.storage.provider=cloudinary 인데 "
                    + "media.storage.cloudinary.{cloud-name,api-key,api-secret} 이 비어 있습니다.");
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name",
                account.cloudName(),
                "api_key",
                account.apiKey(),
                "api_secret",
                account.apiSecret(),
                "secure",
                true,
                "timeout",
                HTTP_TIMEOUT_SECONDS,
                "analytics", // Cloudinary 내부 집계용 파라미터 ?_a= 제외.
                false));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

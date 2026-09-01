package com.gommit.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudinary.Cloudinary;
import com.cloudinary.api.exceptions.NotFound;
import com.cloudinary.utils.ObjectUtils;
import com.gommit.domain.media.config.CloudinaryClientFactory;
import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.config.MediaStorageProperties.CloudinaryAccount;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.domain.media.policy.StoragePolicy.Visibility;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

// 실제 Cloudinary 계정에 대한 왕복 테스트. CI 에서 CLOUDINARY_* 시크릿이 있을 때만 돈다.
// 로컬 `./gradlew test` 에서는 조건 미충족으로 스킵된다.
@DisplayName("CloudinaryStorageService (통합)")
@EnabledIfEnvironmentVariable(named = "CLOUDINARY_CLOUD_NAME", matches = ".+")
class CloudinaryStorageServiceIntegrationTest {

    // 1x1 투명 PNG
    private static final byte[] PNG_1X1 = Base64.getDecoder()
            .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final CloudinaryAccount account = new CloudinaryAccount(
            System.getenv("CLOUDINARY_CLOUD_NAME"),
            System.getenv("CLOUDINARY_API_KEY"),
            System.getenv("CLOUDINARY_API_SECRET"));

    private final Cloudinary cloudinary = CloudinaryClientFactory.create(account);

    private final CloudinaryStorageService service = new CloudinaryStorageService(
            cloudinary,
            new MediaStorageProperties(
                    "cloudinary",
                    null,
                    account,
                    Map.of(
                            MediaRole.CHECKIN,
                            new StoragePolicy(
                                    "gommit-it-test/check-ins",
                                    DataSize.ofMegabytes(5),
                                    Visibility.PRIVATE,
                                    Set.of("image/png")))));

    @Test
    @DisplayName("PRIVATE 이미지 store -> load -> delete 왕복")
    void roundTrip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "x.png", "image/png", PNG_1X1);

        StorageResult stored = service.store(file, MediaRole.CHECKIN);
        assertThat(stored.storageKey()).startsWith("gommit-it-test/check-ins/").endsWith(".png");

        Resource loaded = service.load(stored.storageKey(), MediaRole.CHECKIN);
        assertThat(loaded.getContentAsByteArray()).isEqualTo(PNG_1X1);

        service.delete(stored.storageKey(), MediaRole.CHECKIN);

        // 삭제 검증은 Admin API 로 한다. 배달 URL(service.load) 은 CDN 캐시를 타서
        // destroy 의 invalidate 가 전파되기 전까지 stale 200 을 준다.
        String key = stored.storageKey();
        String publicId = key.substring(0, key.lastIndexOf('.'));
        assertThatThrownBy(() -> cloudinary
                        .api()
                        .resource(publicId, ObjectUtils.asMap("resource_type", "image", "type", "authenticated")))
                .isInstanceOf(NotFound.class);
    }
}

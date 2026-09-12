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
import com.gommit.domain.media.support.MediaContentType;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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

    // 16x16 · 1프레임 · H.264 mp4 (825B). DailyLog 몽타주처럼 서버가 만든 video 바이트를 대신한다.
    private static final byte[] MP4_TINY = Base64.getDecoder()
            .decode(
                    "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAALwbW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAAMgAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAj90cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAAMgAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAABAAAAAQAAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAADIAAAAAAABAAAAAAG3bWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAoAAAACABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABYm1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAASJzdGJsAAAAvnN0c2QAAAAAAAAAAQAAAK5hdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAABAAEABIAAAASAAAAAAAAAABDExhdmMgbGlieDI2NAAAAAAAAAAAAAAAAAAAAAAAAAAAGP//AAAANGF2Y0MBZAAK/+EAF2dkAAqs2V7ARAAAAwAEAAADACg8SJZYAQAGaOvjyyLA/fj4AAAAABBwYXNwAAAAAQAAAAEAAAAUYnRydAAAAAAAAAPoAAAAAAAAABhzdHRzAAAAAAAAAAEAAAABAAAIAAAAABxzdHNjAAAAAAAAAAEAAAABAAAAAQAAAAEAAAAUc3RzegAAAAAAAAAZAAAAAQAAABRzdGNvAAAAAAAAAAEAAAMgAAAAPXVkdGEAAAA1bWV0YQAAAAAAAAAhaGRscgAAAAAAAAAAbWRpcmFwcGwAAAAAAAAAAAAAAAAIaWxzdAAAAAhmcmVlAAAAIW1kYXQAAAAVZYiEAD///uZ1+BTTCBpJMvxDzj+B");

    private final CloudinaryAccount account = new CloudinaryAccount(
            System.getenv("CLOUDINARY_CLOUD_NAME"),
            System.getenv("CLOUDINARY_API_KEY"),
            System.getenv("CLOUDINARY_API_SECRET"),
            "test-go-mmit"); // @SpringBootTest 로 주입 확인할 것 없어서 직접 설정.

    private final Cloudinary cloudinary = CloudinaryClientFactory.create(account);

    private final CloudinaryStorageService service = new CloudinaryStorageService(
            cloudinary,
            new MediaStorageProperties(
                    "cloudinary",
                    null,
                    account,
                    // 통합테스트 산출물은 orphan 발생 등 문제시 폴더째 정리하기 위해 test-go-mmit/integration-test로 격리
                    Map.of(
                            MediaRole.CHECKIN,
                            new StoragePolicy(
                                    "integration-test/check-ins",
                                    DataSize.ofMegabytes(5),
                                    Visibility.PRIVATE,
                                    Set.of("image/png")),
                            MediaRole.DAILYLOG,
                            new StoragePolicy(
                                    "integration-test/daily-check-ins",
                                    DataSize.ofMegabytes(40),
                                    Visibility.PRIVATE,
                                    Set.of("video/mp4")))));

    @Test
    @DisplayName("PRIVATE 이미지 store -> load -> delete 왕복")
    void roundTrip() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", "x.png", "image/png", PNG_1X1);

        StorageResult stored = service.store(file, MediaRole.CHECKIN);
        assertThat(stored.storageKey())
                .startsWith("test-go-mmit/integration-test/check-ins/")
                .endsWith(".png");

        Resource loaded = service.load(stored.storageKey(), MediaRole.CHECKIN);
        assertThat(loaded.getContentAsByteArray()).isEqualTo(PNG_1X1);
        // #51: 파일명을 노출해 서빙 컨트롤러가 MediaTypeFactory 로 Content-Type 을 판정할 수 있어야 한다
        assertThat(loaded.getFilename()).endsWith(".png");
        assertThat(MediaTypeFactory.getMediaType(loaded.getFilename())).contains(MediaType.IMAGE_PNG);

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

    @Test
    @DisplayName("서버 생성 mp4(몽타주) storeGenerated -> load -> delete 왕복 (video 리소스)")
    void generatedVideoRoundTrip() throws Exception {
        StorageResult stored = service.storeGenerated(MP4_TINY, MediaContentType.MP4, MediaRole.DAILYLOG);
        assertThat(stored.storageKey())
                .startsWith("test-go-mmit/integration-test/daily-check-ins/")
                .endsWith(".mp4");

        // 서명 URL 로 다시 받아 온다. Cloudinary video 배달은 리먹싱될 수 있어 바이트 일치 대신
        // mp4 컨테이너 시그니처(ftyp)와 비어 있지 않음만 확인한다.
        Resource loaded = service.load(stored.storageKey(), MediaRole.DAILYLOG);
        byte[] bytes = loaded.getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(100);
        assertThat(new String(bytes, 4, 4, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("ftyp");

        service.delete(stored.storageKey(), MediaRole.DAILYLOG);

        String key = stored.storageKey();
        String publicId = key.substring(0, key.lastIndexOf('.'));
        assertThatThrownBy(() -> cloudinary
                        .api()
                        .resource(publicId, ObjectUtils.asMap("resource_type", "video", "type", "authenticated")))
                .isInstanceOf(NotFound.class);
    }
}

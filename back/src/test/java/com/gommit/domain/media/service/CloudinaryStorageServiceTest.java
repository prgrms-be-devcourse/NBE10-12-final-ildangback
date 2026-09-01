package com.gommit.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.gommit.domain.media.config.CloudinaryClientFactory;
import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.config.MediaStorageProperties.CloudinaryAccount;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.domain.media.policy.StoragePolicy.Visibility;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryStorageService")
class CloudinaryStorageServiceTest {

    private static final MediaStorageProperties PROPERTIES = new MediaStorageProperties(
            "cloudinary",
            null,
            new CloudinaryAccount("democloud", "k", "s"),
            Map.of(
                    MediaRole.ITEM,
                    new StoragePolicy(
                            "character-store", DataSize.ofMegabytes(5), Visibility.PUBLIC, Set.of("image/png")),
                    MediaRole.CHECKIN,
                    new StoragePolicy("check-ins", DataSize.ofMegabytes(5), Visibility.PRIVATE, Set.of("image/png")),
                    MediaRole.DAILYLOG,
                    new StoragePolicy(
                            "daily-check-ins", DataSize.ofMegabytes(40), Visibility.PRIVATE, Set.of("video/mp4"))));

    @Nested
    @DisplayName("store")
    class Store {

        @Mock
        Cloudinary cloudinary;

        @Mock
        Uploader uploader;

        @Test
        @DisplayName("역할 폴더/콘텐츠 타입/공개여부를 업로드 옵션으로 넘기고, storageKey = publicId.format")
        void uploadsWithOptions() throws Exception {
            given(cloudinary.uploader()).willReturn(uploader);
            given(uploader.upload(any(), anyMap()))
                    .willReturn(Map.of("public_id", "check-ins/abc123", "format", "jpg"));

            CloudinaryStorageService service = new CloudinaryStorageService(cloudinary, PROPERTIES);
            MockMultipartFile png = new MockMultipartFile("f", "x", "image/png", new byte[] {1, 2, 3});

            String key = service.store(png, MediaRole.CHECKIN).storageKey();

            assertThat(key).isEqualTo("check-ins/abc123.jpg");

            ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
            verify(uploader).upload(any(), options.capture());
            assertThat(options.getValue())
                    .containsEntry("folder", "check-ins")
                    .containsEntry("resource_type", "image")
                    .containsEntry("type", "authenticated"); // CHECKIN 은 PRIVATE
        }

        @Test
        @DisplayName("영상 역할은 resource_type=video, PUBLIC 역할은 type=upload")
        void videoAndPublic() throws Exception {
            given(cloudinary.uploader()).willReturn(uploader);
            given(uploader.upload(any(), anyMap()))
                    .willReturn(Map.of("public_id", "character-store/i", "format", "png"));

            CloudinaryStorageService service = new CloudinaryStorageService(cloudinary, PROPERTIES);
            service.store(new MockMultipartFile("f", "x", "image/png", new byte[] {1}), MediaRole.ITEM);

            ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
            verify(uploader).upload(any(), options.capture());
            assertThat(options.getValue()).containsEntry("type", "upload").containsEntry("resource_type", "image");
        }

        @Test
        @DisplayName("허용되지 않는 콘텐츠 타입이면 UNSUPPORTED_MEDIA_TYPE")
        void unsupportedType() {
            CloudinaryStorageService service = new CloudinaryStorageService(cloudinary, PROPERTIES);
            MockMultipartFile gif = new MockMultipartFile("f", "x", "image/gif", new byte[] {1});
            assertThatThrownBy(() -> service.store(gif, MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Mock
        Cloudinary cloudinary;

        @Mock
        Uploader uploader;

        @Test
        @DisplayName("publicId 와 resource_type/type/invalidate 로 destroy 를 호출한다")
        void destroys() throws Exception {
            given(cloudinary.uploader()).willReturn(uploader);
            given(uploader.destroy(eq("check-ins/abc123"), anyMap())).willReturn(Map.of("result", "ok"));

            CloudinaryStorageService service = new CloudinaryStorageService(cloudinary, PROPERTIES);
            service.delete("check-ins/abc123.jpg", MediaRole.CHECKIN);

            ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
            verify(uploader).destroy(eq("check-ins/abc123"), options.capture());
            assertThat(options.getValue())
                    .containsEntry("resource_type", "image")
                    .containsEntry("type", "authenticated")
                    .containsEntry("invalidate", true);
        }
    }

    @Nested
    @DisplayName("publicUrl")
    class PublicUrl {

        // URL 생성은 순수 계산이므로 실제 Cloudinary 클라이언트(운영과 동일 설정)를 쓴다.
        private final CloudinaryStorageService service = new CloudinaryStorageService(
                CloudinaryClientFactory.create(new CloudinaryAccount("democloud", "k", "s")), PROPERTIES);

        @Test
        @DisplayName("PUBLIC 이미지는 image/upload 배달 URL (서명·분석 파라미터 없음)")
        void publicImage() {
            String url = service.publicUrl("character-store/abc.png");
            assertThat(url)
                    .startsWith("https://res.cloudinary.com/democloud/image/upload/")
                    .contains("/character-store/abc.png")
                    .doesNotContain("/s--") // 서명 없음
                    .doesNotContain("?_a="); // 분석 파라미터 없음
        }

        @Test
        @DisplayName("확장자가 없는 키는 MEDIA_NOT_FOUND")
        void malformedKey() {
            assertThatThrownBy(() -> service.publicUrl("nodot"))
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
        }
    }
}

package com.gommit.domain.media.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.domain.media.config.MediaStorageProperties;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@DisplayName("MediaValidator")
class MediaValidatorTest {

    // 유효한 매직바이트 프리픽스
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final MediaValidator validator = new MediaValidator(properties(DataSize.ofMegabytes(5)));

    @Test
    @DisplayName("빈 파일이면 EMPTY_FILE")
    void emptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> validator.validate(empty, MediaRole.CHECKIN))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
    }

    @Test
    @DisplayName("파일이 null 이어도 EMPTY_FILE")
    void nullFile() {
        assertThatThrownBy(() -> validator.validate(null, MediaRole.CHECKIN))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.EMPTY_FILE));
    }

    @Test
    @DisplayName("정책 용량을 넘으면 FILE_TOO_LARGE")
    void tooLarge() {
        MediaValidator tightLimit = new MediaValidator(properties(DataSize.ofBytes(100)));
        MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", padded(PNG_MAGIC, 200));
        assertThatThrownBy(() -> tightLimit.validate(big, MediaRole.CHECKIN))
                .satisfies(
                        e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));
    }

    @Nested
    @DisplayName("콘텐츠 타입")
    class ContentType {

        @Test
        @DisplayName("정책 화이트리스트에 없는 타입이면 UNSUPPORTED_MEDIA_TYPE")
        void notAllowed() {
            // CHECKIN 정책은 png/jpeg 만 허용, webp 는 불가
            MockMultipartFile webp = new MockMultipartFile("file", "a.webp", "image/webp", padded(PNG_MAGIC, 32));
            assertThatThrownBy(() -> validator.validate(webp, MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }

        @Test
        @DisplayName("Content-Type 헤더가 없으면 UNSUPPORTED_MEDIA_TYPE")
        void missing() {
            MockMultipartFile noType = new MockMultipartFile("file", "a.png", null, padded(PNG_MAGIC, 32));
            assertThatThrownBy(() -> validator.validate(noType, MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }

        @Test
        @DisplayName("선언한 타입과 실제 매직바이트가 다르면 UNSUPPORTED_MEDIA_TYPE (위조 방지)")
        void magicByteMismatch() {
            // image/png 라고 선언했지만 내용은 JPEG
            MockMultipartFile spoofed = new MockMultipartFile("file", "a.png", "image/png", padded(JPEG_MAGIC, 32));
            assertThatThrownBy(() -> validator.validate(spoofed, MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }
    }

    @Test
    @DisplayName("타입·용량·매직바이트가 모두 맞으면 통과한다")
    void valid() {
        MockMultipartFile png = new MockMultipartFile("file", "a.png", "image/png", padded(PNG_MAGIC, 64));
        assertThatCode(() -> validator.validate(png, MediaRole.CHECKIN)).doesNotThrowAnyException();
    }

    private static MediaStorageProperties properties(DataSize checkinMaxSize) {
        return new MediaStorageProperties(
                "local",
                new MediaStorageProperties.LocalPaths("./x", "http://localhost/media"),
                null,
                Map.of(
                        MediaRole.CHECKIN,
                        new StoragePolicy(
                                "check-ins", checkinMaxSize, Visibility.PRIVATE, Set.of("image/png", "image/jpeg")),
                        MediaRole.ITEM,
                        new StoragePolicy(
                                "character-store",
                                DataSize.ofMegabytes(5),
                                Visibility.PUBLIC,
                                Set.of("image/png", "image/jpeg", "image/webp"))));
    }

    // magic 뒤에 0 을 채워 원하는 길이로 만든다 (검증기는 선두 12바이트만 읽는다).
    private static byte[] padded(byte[] magic, int totalLength) {
        byte[] result = new byte[totalLength];
        System.arraycopy(magic, 0, result, 0, Math.min(magic.length, totalLength));
        return result;
    }
}

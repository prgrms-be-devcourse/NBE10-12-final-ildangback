package com.gommit.domain.media.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MediaContentType")
class MediaContentTypeTest {

    @Nested
    @DisplayName("fromMimeType")
    class FromMimeType {

        @Test
        @DisplayName("알려진 MIME 타입을 enum 으로 되찾는다")
        void known() {
            assertThat(MediaContentType.fromMimeType("image/png")).contains(MediaContentType.PNG);
            assertThat(MediaContentType.fromMimeType("image/jpeg")).contains(MediaContentType.JPEG);
            assertThat(MediaContentType.fromMimeType("image/webp")).contains(MediaContentType.WEBP);
            assertThat(MediaContentType.fromMimeType("video/mp4")).contains(MediaContentType.MP4);
        }

        @Test
        @DisplayName("대소문자를 무시한다")
        void caseInsensitive() {
            assertThat(MediaContentType.fromMimeType("IMAGE/PNG")).contains(MediaContentType.PNG);
        }

        @Test
        @DisplayName("모르는 타입이나 null 이면 비어 있다")
        void unknown() {
            assertThat(MediaContentType.fromMimeType("image/gif")).isEmpty();
            assertThat(MediaContentType.fromMimeType(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fromExtension")
    class FromExtension {

        @Test
        @DisplayName("확장자로 enum 을 되찾는다")
        void known() {
            assertThat(MediaContentType.fromExtension("png")).contains(MediaContentType.PNG);
            assertThat(MediaContentType.fromExtension("jpg")).contains(MediaContentType.JPEG);
            assertThat(MediaContentType.fromExtension("webp")).contains(MediaContentType.WEBP);
            assertThat(MediaContentType.fromExtension("mp4")).contains(MediaContentType.MP4);
        }

        @Test
        @DisplayName("jpeg 는 jpg 로 정규화한다")
        void jpegNormalizedToJpg() {
            assertThat(MediaContentType.fromExtension("jpeg")).contains(MediaContentType.JPEG);
            assertThat(MediaContentType.fromExtension("JPEG")).contains(MediaContentType.JPEG);
        }

        @Test
        @DisplayName("모르는 확장자나 null 이면 비어 있다")
        void unknown() {
            assertThat(MediaContentType.fromExtension("gif")).isEmpty();
            assertThat(MediaContentType.fromExtension(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("메타데이터")
    class Metadata {

        @Test
        @DisplayName("isVideo 는 MP4 만 true")
        void isVideo() {
            assertThat(MediaContentType.MP4.isVideo()).isTrue();
            assertThat(MediaContentType.PNG.isVideo()).isFalse();
            assertThat(MediaContentType.JPEG.isVideo()).isFalse();
            assertThat(MediaContentType.WEBP.isVideo()).isFalse();
        }
    }

    @Nested
    @DisplayName("matchesSignature")
    class MatchesSignature {

        @Test
        @DisplayName("PNG 시그니처")
        void png() {
            byte[] header = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0);
            assertThat(MediaContentType.PNG.matchesSignature(header)).isTrue();
            assertThat(MediaContentType.JPEG.matchesSignature(header)).isFalse();
        }

        @Test
        @DisplayName("JPEG 시그니처")
        void jpeg() {
            byte[] header = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0);
            assertThat(MediaContentType.JPEG.matchesSignature(header)).isTrue();
            assertThat(MediaContentType.PNG.matchesSignature(header)).isFalse();
        }

        @Test
        @DisplayName("WEBP 는 RIFF....WEBP")
        void webp() {
            byte[] webp = bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50);
            assertThat(MediaContentType.WEBP.matchesSignature(webp)).isTrue();

            byte[] riffButNotWebp = bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20);
            assertThat(MediaContentType.WEBP.matchesSignature(riffButNotWebp)).isFalse();
        }

        @Test
        @DisplayName("MP4 는 offset 4 에 ftyp + mp4 계열 브랜드")
        void mp4() {
            byte[] mp4 = bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D); // ....ftypisom
            assertThat(MediaContentType.MP4.matchesSignature(mp4)).isTrue();
        }

        @Test
        @DisplayName("ftyp 이어도 HEIC/mov 브랜드는 거부한다 (Live Photo 정지사진 등)")
        void mp4RejectsHeicAndMovBrands() {
            byte[] heic = bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x68, 0x65, 0x69, 0x63); // ftyp heic
            byte[] mov = bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x71, 0x74, 0x20, 0x20); // ftyp "qt  "
            assertThat(MediaContentType.MP4.matchesSignature(heic)).isFalse();
            assertThat(MediaContentType.MP4.matchesSignature(mov)).isFalse();
        }

        @Test
        @DisplayName("헤더가 너무 짧으면 모두 false")
        void tooShort() {
            byte[] tiny = bytes(0x89, 0x50);
            for (MediaContentType type : MediaContentType.values()) {
                assertThat(type.matchesSignature(tiny)).isFalse();
            }
            for (MediaContentType type : MediaContentType.values()) {
                assertThat(type.matchesSignature(new byte[0])).isFalse();
            }
        }
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}

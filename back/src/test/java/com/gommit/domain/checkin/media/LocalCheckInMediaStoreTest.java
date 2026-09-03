package com.gommit.domain.checkin.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

@DisplayName("LocalCheckInMediaStore — 로컬 파일 저장/조회")
class LocalCheckInMediaStoreTest {

    private LocalCheckInMediaStore store;
    private Path baseDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        this.baseDir = tempDir;
        this.store = new LocalCheckInMediaStore(tempDir.toString());
    }

    private static MockMultipartFile png(byte[] bytes) {
        return new MockMultipartFile("media", "shot.png", "image/png", bytes);
    }

    private static void assertErrorCode(Throwable thrown, ErrorCode expected) {
        assertThat(thrown)
                .asInstanceOf(InstanceOfAssertFactories.type(BusinessException.class))
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("reserve 는 check-ins/{yyyy}/{MM}/{uuid}.{ext} 키만 돌려주고 파일은 쓰지 않는다")
    void reserveReturnsKeyWithoutWriting() throws IOException {
        String key = store.reserve(png(new byte[] {1, 2, 3}));

        assertThat(key).matches("check-ins/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        try (var walk = Files.walk(baseDir)) {
            assertThat(walk.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    @DisplayName("reserve 로 받은 키에 write 하면 그 키로 다시 읽을 수 있다")
    void writeThenLoad() throws IOException {
        byte[] bytes = {10, 20, 30, 40};
        MockMultipartFile file = png(bytes);
        String key = store.reserve(file);

        store.write(file, key);

        assertThat(store.load(key).getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("reserve — 빈 파일은 EMPTY_FILE")
    void rejectsEmpty() {
        assertThatThrownBy(() -> store.reserve(png(new byte[0])))
                .satisfies(e -> assertErrorCode(e, ErrorCode.EMPTY_FILE));
    }

    @Test
    @DisplayName("reserve — 5MB 를 넘으면 FILE_TOO_LARGE")
    void rejectsTooLarge() {
        MockMultipartFile big = png(new byte[5 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> store.reserve(big)).satisfies(e -> assertErrorCode(e, ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("reserve — 허용하지 않는 Content-Type 은 UNSUPPORTED_MEDIA_TYPE")
    void rejectsUnsupportedType() {
        MockMultipartFile pdf = new MockMultipartFile("media", "doc.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> store.reserve(pdf))
                .satisfies(e -> assertErrorCode(e, ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    @DisplayName("reserve — png/jpeg/webp 확장자로 매핑된다")
    void mapsExtensions() {
        assertThat(store.reserve(new MockMultipartFile("m", "a.png", "image/png", new byte[] {1})))
                .endsWith(".png");
        assertThat(store.reserve(new MockMultipartFile("m", "a.jpg", "image/jpeg", new byte[] {1})))
                .endsWith(".jpg");
        assertThat(store.reserve(new MockMultipartFile("m", "a.webp", "image/webp", new byte[] {1})))
                .endsWith(".webp");
    }

    @Test
    @DisplayName("load — 없는 키는 MEDIA_NOT_FOUND")
    void loadMissing() {
        assertThatThrownBy(() -> store.load("check-ins/2026/09/does-not-exist.png"))
                .satisfies(e -> assertErrorCode(e, ErrorCode.MEDIA_NOT_FOUND));
    }

    @Test
    @DisplayName("경로 탈출(../) 키는 ACCESS_DENIED")
    void rejectsPathTraversal() {
        MockMultipartFile file = png(new byte[] {1});
        assertThatThrownBy(() -> store.load("../../etc/passwd"))
                .satisfies(e -> assertErrorCode(e, ErrorCode.ACCESS_DENIED));
        assertThatThrownBy(() -> store.write(file, "../../etc/passwd"))
                .satisfies(e -> assertErrorCode(e, ErrorCode.ACCESS_DENIED));
    }
}

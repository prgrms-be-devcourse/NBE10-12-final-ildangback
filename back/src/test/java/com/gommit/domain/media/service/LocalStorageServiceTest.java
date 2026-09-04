package com.gommit.domain.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.domain.media.policy.StoragePolicy.Visibility;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

@DisplayName("LocalStorageService")
class LocalStorageServiceTest {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4};

    @TempDir
    Path baseDir;

    private LocalStorageService service;

    @BeforeEach
    void setUp() {
        service = new LocalStorageService(new MediaStorageProperties(
                "local",
                new MediaStorageProperties.LocalPaths(baseDir.toString(), "http://localhost:8080/media/"),
                null,
                Map.of(
                        MediaRole.ITEM,
                        new StoragePolicy(
                                "character-store", DataSize.ofMegabytes(5), Visibility.PUBLIC, Set.of("image/png")),
                        MediaRole.CHECKIN,
                        new StoragePolicy(
                                "check-ins", DataSize.ofMegabytes(5), Visibility.PRIVATE, Set.of("image/png")))));
    }

    private MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "orig.png", "image/png", PNG_BYTES);
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("키는 {folder}/{yyyy}/{MM}/{uuid}.{ext} 형식이다")
        void keyFormat() {
            StorageResult result = service.store(pngFile(), MediaRole.CHECKIN);
            assertThat(result.storageKey()).matches("check-ins/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.png");
        }

        @Test
        @DisplayName("실제 파일을 baseDir 아래에 쓴다")
        void writesFile() throws IOException {
            StorageResult result = service.store(pngFile(), MediaRole.CHECKIN);
            Path written = baseDir.resolve(result.storageKey());
            assertThat(written).exists();
            assertThat(Files.readAllBytes(written)).isEqualTo(PNG_BYTES);
        }

        @Test
        @DisplayName("허용되지 않는 콘텐츠 타입이면 UNSUPPORTED_MEDIA_TYPE")
        void unsupportedContentType() {
            MockMultipartFile pdf = new MockMultipartFile("file", "x.pdf", "application/pdf", PNG_BYTES);
            assertThatThrownBy(() -> service.store(pdf, MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        }

        @Test
        @DisplayName("파일 쓰기가 실패하면 MEDIA_STORAGE_FAILED")
        void writeFailure() throws IOException {
            // baseDir 자리에 일반 파일을 두면 하위 디렉토리 생성이 IOException 으로 실패한다
            Path notADir = baseDir.resolve("not-a-dir");
            Files.writeString(notADir, "x");
            LocalStorageService broken = new LocalStorageService(new MediaStorageProperties(
                    "local",
                    new MediaStorageProperties.LocalPaths(notADir.toString(), "http://localhost/media"),
                    null,
                    Map.of(
                            MediaRole.CHECKIN,
                            new StoragePolicy(
                                    "check-ins", DataSize.ofMegabytes(5), Visibility.PRIVATE, Set.of("image/png")))));

            assertThatThrownBy(() -> broken.store(pngFile(), MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.MEDIA_STORAGE_FAILED));
        }

        @Test
        @DisplayName("확장자는 Content-Type 에서 정한다 (jpeg -> jpg)")
        void extensionFromContentType() {
            MockMultipartFile jpeg = new MockMultipartFile("file", "x", "image/jpeg", PNG_BYTES);
            // ITEM 정책이 jpeg 를 허용하도록 별도 서비스
            LocalStorageService jpegOk = new LocalStorageService(new MediaStorageProperties(
                    "local",
                    new MediaStorageProperties.LocalPaths(baseDir.toString(), "http://localhost/media"),
                    null,
                    Map.of(
                            MediaRole.ITEM,
                            new StoragePolicy(
                                    "character-store",
                                    DataSize.ofMegabytes(5),
                                    Visibility.PUBLIC,
                                    Set.of("image/jpeg")))));
            assertThat(jpegOk.store(jpeg, MediaRole.ITEM).storageKey()).endsWith(".jpg");
        }
    }

    @Nested
    @DisplayName("load")
    class Load {

        @Test
        @DisplayName("저장한 파일을 그대로 읽는다")
        void readsStored() throws IOException {
            String key = service.store(pngFile(), MediaRole.CHECKIN).storageKey();
            assertThat(service.load(key, MediaRole.CHECKIN).getContentAsByteArray())
                    .isEqualTo(PNG_BYTES);
        }

        @Test
        @DisplayName("없는 키면 MEDIA_NOT_FOUND")
        void missing() {
            assertThatThrownBy(() -> service.load("check-ins/2026/01/none.png", MediaRole.CHECKIN))
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MEDIA_NOT_FOUND));
        }

        @Test
        @DisplayName("경로 탈출(../)은 ACCESS_DENIED 로 차단한다")
        void pathTraversalBlocked() {
            assertThatThrownBy(() -> service.load("../../../etc/passwd", MediaRole.CHECKIN))
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        }

        @Test
        @DisplayName("절대경로 키도 ACCESS_DENIED 로 차단한다")
        void absolutePathBlocked() {
            assertThatThrownBy(() -> service.load("/etc/passwd", MediaRole.CHECKIN))
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("파일을 지운다")
        void removes() {
            String key = service.store(pngFile(), MediaRole.CHECKIN).storageKey();
            service.delete(key, MediaRole.CHECKIN);
            assertThat(baseDir.resolve(key)).doesNotExist();
        }

        @Test
        @DisplayName("삭제가 IOException 으로 실패하면 MEDIA_STORAGE_FAILED")
        void deleteFailure() throws IOException {
            // 키가 비어 있지 않은 디렉토리를 가리키면 deleteIfExists 가 IOException 을 던진다
            Path dir = baseDir.resolve("check-ins/sub");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("child.txt"), "x");

            assertThatThrownBy(() -> service.delete("check-ins/sub", MediaRole.CHECKIN))
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ErrorCode.MEDIA_STORAGE_FAILED));
        }

        @Test
        @DisplayName("없는 파일을 지워도 예외가 없다 (멱등)")
        void idempotent() {
            assertThatCode(() -> service.delete("check-ins/2026/01/none.png", MediaRole.CHECKIN))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("마지막 파일을 지우면 빈 yyyy/MM 디렉토리도 정리한다")
        void prunesEmptyDirs() {
            String key = service.store(pngFile(), MediaRole.CHECKIN).storageKey();
            Path monthDir = baseDir.resolve(key).getParent();
            Path yearDir = monthDir.getParent();

            service.delete(key, MediaRole.CHECKIN);

            assertThat(monthDir).doesNotExist();
            assertThat(yearDir).doesNotExist();
            assertThat(baseDir).exists();
        }

        @Test
        @DisplayName("같은 디렉토리에 다른 파일이 남으면 디렉토리는 유지한다")
        void keepsNonEmptyDirs() {
            String key1 = service.store(pngFile(), MediaRole.CHECKIN).storageKey();
            String key2 = service.store(pngFile(), MediaRole.CHECKIN).storageKey();
            Path monthDir = baseDir.resolve(key1).getParent();

            service.delete(key1, MediaRole.CHECKIN);

            assertThat(monthDir).exists();
            assertThat(baseDir.resolve(key2)).exists();
        }
    }

    @Nested
    @DisplayName("publicUrl")
    class PublicUrl {

        @Test
        @DisplayName("base-url + key, 끝 슬래시는 정리한다")
        void buildsUrl() {
            // setUp 의 base-url 은 끝에 "/" 가 붙어 있다
            assertThat(service.publicUrl("character-store/2026/09/x.png"))
                    .isEqualTo("http://localhost:8080/media/character-store/2026/09/x.png");
        }
    }
}

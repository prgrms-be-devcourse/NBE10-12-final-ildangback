package com.gommit.domain.checkin.media;

import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// MVP 임시 구현 — 로컬 파일시스템에 바이트를 저장한다.#8 머지시 90% 삭제, 어댑터만 남음
// 정책(5MB / 이미지 3종)과 키 포맷("check-ins/yyyy/MM/{uuid}.{ext}")을 #8(media) 의
// LocalStorageService + media.storage.policies.CHECKIN 과 맞춰뒀다.
// #8 머지 시: 이 클래스를 media.StorageService(file/key, MediaRole.CHECKIN) 위임 어댑터로 교체하고
//            checkin.media.base-dir 프로퍼티를 제거한다. 포트·서빙 경로는 유지.
// 매직바이트 검증은 #8 MediaValidator 소관 — 여기서는 선언 Content-Type + 확장자 화이트리스트만 검사한다.
@Component
public class LocalCheckInMediaStore implements CheckInMediaStore {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // #8 policies.CHECKIN.max-size
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final String FOLDER = "check-ins";

    // 허용 Content-Type → 저장 확장자. #8 MediaContentType 와 동일 매핑.
    private static final Map<String, String> ALLOWED = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp");

    private final Path baseDir;

    public LocalCheckInMediaStore(@Value("${checkin.media.base-dir:./data/media}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public String reserve(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_FILE);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String extension = ALLOWED.get(contentType(file));
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        LocalDate today = LocalDate.now();
        return "%s/%s/%s/%s.%s"
                .formatted(FOLDER, today.format(YEAR), today.format(MONTH), UUID.randomUUID(), extension);
    }

    @Override
    public void write(MultipartFile file, String storageKey) {
        Path destination = resolve(storageKey);
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination);
            }
        } catch (IOException e) {
            deleteQuietly(destination); // 중간까지 쓰인 파일 잔여물 정리 (best-effort)
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String storageKey) {
        Resource resource = new FileSystemResource(resolve(storageKey));
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return resource;
    }

    private static String contentType(MultipartFile file) {
        String type = file.getContentType();
        return type == null ? null : type.toLowerCase(Locale.ROOT);
    }

    // storageKey 를 baseDir 아래 절대경로로 변환. 경로 탈출(../)은 ACCESS_DENIED 로 차단한다.
    private Path resolve(String storageKey) {
        Path path = baseDir.resolve(storageKey).normalize();
        if (!path.startsWith(baseDir)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return path;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 저장 실패를 알리는 원래 예외를 덮지 않는다.
        }
    }
}

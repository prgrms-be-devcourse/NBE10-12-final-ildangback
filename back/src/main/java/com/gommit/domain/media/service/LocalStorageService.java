package com.gommit.domain.media.service;

import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.domain.media.support.MediaContentType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// 파일 경로: {baseDir}/{folder}/{yyyy}/{MM}/{uuid}.{ext}
// storageKey = baseDir 를 뺀 상대경로. 키로 파일을 특정 가능 -> mediaRole 미사용.
// PUBLIC 은 MediaLocalResourceConfig 가 {baseUrl}/{key} 로 정적 서빙, publicUrl() 이 해당 URL 반환(PUBLIC 키 전용).
public class LocalStorageService implements StorageService {

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");

    private final MediaStorageProperties properties;
    private final Path baseDir;
    private final String baseUrl;

    public LocalStorageService(MediaStorageProperties properties) {
        this.properties = properties;
        this.baseDir = Path.of(properties.local().baseDir()).toAbsolutePath().normalize();
        this.baseUrl = stripTrailingSlash(properties.local().baseUrl());
    }

    // 호출 전에 MediaValidator.validate() 로 검증돼 있어야 한다.
    // 용량·매직바이트를 다시 확인하지 않고, 확장자는 선언된 Content-Type 에서만 가져온다.
    @Override
    public StorageResult store(MultipartFile file, MediaRole mediaRole) {
        StoragePolicy policy = properties.policyFor(mediaRole);
        String extension = MediaContentType.fromMimeType(file.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE))
                .extension();

        LocalDate today = LocalDate.now();
        String storageKey = "%s/%s/%s/%s.%s"
                .formatted(policy.folder(), today.format(YEAR), today.format(MONTH), UUID.randomUUID(), extension);

        Path destination = resolve(storageKey);
        try {
            Files.createDirectories(destination.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, destination);
            }
        } catch (IOException e) {
            deleteQuietly(destination); // 중간까지 쓰인 파일 잔여물 제거
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }

        return new StorageResult(storageKey);
    }

    @Override
    public Resource load(String storageKey, MediaRole mediaRole) {
        Resource resource = new FileSystemResource(resolve(storageKey));
        if (!resource.exists() || !resource.isReadable()) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return resource;
    }

    @Override
    public void delete(String storageKey, MediaRole mediaRole) {
        Path target = resolve(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
        pruneEmptyDirs(target.getParent());
    }

    // PUBLIC storageKey 전용. PRIVATE 사용시 404 나옴.
    @Override
    public String publicUrl(String storageKey) {
        return baseUrl + "/" + storageKey;
    }

    // storageKey 를 baseDir 아래 절대경로로 변환. 경로 탈출(../) 은 ACCESS_DENIED 로 차단한다.
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
            // 메소드를 부른 기존 예외를 이 예외로 덮어쓰지 않음
        }
    }

    // 삭제 후 남은 빈 {yyyy}/{MM} 등 디렉토리를 baseDir 바로 아래까지 올라가며 제거한다.
    // 경합으로 실패해도 무시한다: 파일 삭제는 이미 성공했고 정리는 best-effort.
    private void pruneEmptyDirs(Path dir) {
        Path current = dir;
        while (current != null && current.startsWith(baseDir) && !current.equals(baseDir)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
                Files.delete(current);
            } catch (IOException e) {
                return;
            }
            current = current.getParent();
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}

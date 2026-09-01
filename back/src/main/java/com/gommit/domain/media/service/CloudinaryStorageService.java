package com.gommit.domain.media.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.gommit.domain.media.config.MediaStorageProperties;
import com.gommit.domain.media.dto.StorageResult;
import com.gommit.domain.media.entity.MediaRole;
import com.gommit.domain.media.policy.StoragePolicy;
import com.gommit.domain.media.support.MediaContentType;
import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

// Cloudinary 스토리지 구현.
// storageKey = "{publicId}.{format}" (로컬 키처럼 확장자를 붙여 두 provider 를 대칭으로 만든다).
//   - resource_type(image/video)은 storageKey 의 확장자로 판단한다.
//   - type(upload/authenticated)은 역할의 StoragePolicy.visibility 로 판단한다.
// PRIVATE 리소스는 서버가 서명 URL 을 만들어 그 자리에서 바이트를 받아 온다(load). 서명 URL 은 클라이언트에 노출되지 않는다.
public class CloudinaryStorageService implements StorageService {

    private static final String TYPE_PUBLIC = "upload";
    private static final String TYPE_PRIVATE = "authenticated";

    private final MediaStorageProperties properties;
    private final Cloudinary cloudinary;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Cloudinary 클라이언트는 MediaConfig 가 CloudinaryClientFactory 로 만들어 주입한다.
    public CloudinaryStorageService(Cloudinary cloudinary, MediaStorageProperties properties) {
        this.cloudinary = cloudinary;
        this.properties = properties;
    }

    @Override
    public StorageResult store(MultipartFile file, MediaRole mediaRole) {
        StoragePolicy policy = properties.policyFor(mediaRole);
        MediaContentType contentType = MediaContentType.fromMimeType(file.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));

        try {
            Map<?, ?> result = cloudinary
                    .uploader()
                    .upload(
                            file.getBytes(),
                            ObjectUtils.asMap(
                                    "folder",
                                    policy.folder(),
                                    "resource_type",
                                    contentType.isVideo() ? "video" : "image",
                                    "type",
                                    deliveryType(policy),
                                    "use_filename",
                                    false,
                                    "unique_filename",
                                    true,
                                    "overwrite",
                                    false));
            return new StorageResult(result.get("public_id") + "." + result.get("format"));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    @Override
    public Resource load(String storageKey, MediaRole mediaRole) {
        String signedUrl = buildUrl(storageKey, deliveryType(properties.policyFor(mediaRole)), true);
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(signedUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
            }
            return new ByteArrayResource(response.body());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    @Override
    public void delete(String storageKey, MediaRole mediaRole) {
        StoragePolicy policy = properties.policyFor(mediaRole);
        String[] parts = split(storageKey);
        try {
            cloudinary
                    .uploader()
                    .destroy(
                            parts[0],
                            ObjectUtils.asMap(
                                    "resource_type", resourceType(parts[1]),
                                    "type", deliveryType(policy),
                                    "invalidate", true));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    // PUBLIC storageKey 전용. type=upload · 서명 없음으로 고정
    @Override
    public String publicUrl(String storageKey) {
        return buildUrl(storageKey, TYPE_PUBLIC, false);
    }

    // storageKey -> Cloudinary 배달 URL. signed=true 면 서명을 붙여 authenticated 리소스 접근 가능.
    private String buildUrl(String storageKey, String deliveryType, boolean signed) {
        String[] parts = split(storageKey);
        return cloudinary
                .url()
                .resourceType(resourceType(parts[1]))
                .type(deliveryType)
                .secure(true)
                .signed(signed)
                .format(parts[1])
                .generate(parts[0]);
    }

    private static String deliveryType(StoragePolicy policy) {
        return policy.isPublic() ? TYPE_PUBLIC : TYPE_PRIVATE;
    }

    // "{publicId}.{format}" -> [publicId, format]
    private static String[] split(String storageKey) {
        int dot = storageKey.lastIndexOf('.');
        if (dot <= 0 || dot == storageKey.length() - 1) {
            throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
        }
        return new String[] {storageKey.substring(0, dot), storageKey.substring(dot + 1)};
    }

    private static String resourceType(String extension) {
        return MediaContentType.fromExtension(extension)
                        .filter(MediaContentType::isVideo)
                        .isPresent()
                ? "video"
                : "image";
    }
}

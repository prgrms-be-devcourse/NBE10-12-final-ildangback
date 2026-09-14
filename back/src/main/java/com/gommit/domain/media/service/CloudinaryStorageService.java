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
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

// Cloudinary 스토리지 구현.
// storageKey = "{publicId}.{format}" (로컬 키처럼 확장자를 붙여 두 provider 를 대칭으로 만든다).
//   - resource_type(image/video)은 storageKey 의 확장자로 판단한다.
//   - type(upload/authenticated)은 역할의 StoragePolicy.visibility 로 판단한다.
// PRIVATE 리소스는 서버가 서명 URL 을 만들어 그 자리에서 바이트를 받아 온다(load). 서명 URL 은 클라이언트에 노출되지 않는다.
public class CloudinaryStorageService implements StorageService {

    private static final String TYPE_PUBLIC = "upload";
    private static final String TYPE_PRIVATE = "authenticated";

    // PRIVATE 미디어를 서버가 Cloudinary 서명 URL 로 받아 올 때의 타임아웃.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final MediaStorageProperties properties;
    private final Cloudinary cloudinary;
    private final RestClient restClient;

    // Cloudinary 클라이언트는 MediaConfig 가 CloudinaryClientFactory 로 만들어 주입한다.
    // RestClient.Builder 없이 만들 때는 Boot 오토컨피그 기본 빌더를 새로 하나 뽑아 쓴다 —
    // load() 는 baseUrl 없이 매번 절대 URL(서명 URL)로 요청하므로 그걸로 충분하다.
    public CloudinaryStorageService(Cloudinary cloudinary, MediaStorageProperties properties) {
        this(cloudinary, properties, RestClient.builder());
    }

    public CloudinaryStorageService(
            Cloudinary cloudinary, MediaStorageProperties properties, RestClient.Builder restClientBuilder) {
        this(cloudinary, properties, restClientBuilder, CONNECT_TIMEOUT, REQUEST_TIMEOUT);
    }

    // 테스트 전용 — 특성화 테스트가 느린 응답 케이스를 30초 실측 대기 없이 짧은 타임아웃으로 검증할 수 있게 함.
    // 운영 경로(2-arg/3-arg 생성자)는 항상 위 CONNECT_TIMEOUT/REQUEST_TIMEOUT(5s/30s)을 강제 적용한다 —
    // 이 메서드가 주입받은 RestClient.Builder 의 requestFactory 를 그대로 덮어써서 강제하는 것이라,
    // 3-arg 생성자에 타임아웃 없는 빌더를 넘겨도(Boot 오토컨피그 기본 빌더 등) 무제한 대기로 새지 않는다.
    // (반대로 이 성질 때문에 3-arg 생성자로는 테스트가 원하는 "더 짧은" 타임아웃을 못 넣는다 — 그래서
    // 이 package-private 오버로드가 따로 필요하다.)
    CloudinaryStorageService(
            Cloudinary cloudinary,
            MediaStorageProperties properties,
            RestClient.Builder restClientBuilder,
            Duration connectTimeout,
            Duration requestTimeout) {
        this.cloudinary = cloudinary;
        this.properties = properties;
        this.restClient = restClientBuilder
                .requestFactory(requestFactory(connectTimeout, requestTimeout))
                .build();
    }

    // Boot 4.x 방식 ClientHttpRequestFactory 구성. 생성자와 특성화 테스트(짧은 타임아웃 재현)가
    // 이 메서드를 공유해서 같은 구성 체인이 두 곳에 따로 복제되는 걸 막는다.
    static ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration requestTimeout) {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(HttpClientSettings.defaults()
                        .withConnectTimeout(connectTimeout)
                        .withReadTimeout(requestTimeout));
    }

    @Override
    public StorageResult store(MultipartFile file, MediaRole mediaRole) {
        MediaContentType contentType = MediaContentType.fromMimeType(file.getContentType())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE));
        try {
            return upload(file.getBytes(), contentType, mediaRole);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    // 서버 생성 바이트 저장(업로드 아님) — DailyLog 몽타주처럼 사용자 파일이 아닌 콘텐츠.
    @Override
    public StorageResult storeGenerated(byte[] content, MediaContentType contentType, MediaRole mediaRole) {
        return upload(content, contentType, mediaRole);
    }

    private StorageResult upload(byte[] content, MediaContentType contentType, MediaRole mediaRole) {
        StoragePolicy policy = properties.policyFor(mediaRole);
        try {
            Map<?, ?> result = cloudinary
                    .uploader()
                    .upload(
                            content,
                            ObjectUtils.asMap(
                                    "folder",
                                    uploadFolderFor(policy),
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
        return fetchResource(signedUrl, filenameOf(storageKey));
    }

    // URL 계산(buildUrl)과 분리된 GET + 상태코드/예외 매핑 부분. Cloudinary 서명 URL 생성기가
    // res.cloudinary.com 도메인을 강제하기 때문에, 클라이언트 종류(HttpClient/RestClient)에
    // 안 가리는 와이어레벨 특성화 테스트가 임의 URL(로컬 HttpServer)을 직접 넣을 수 있도록
    // package-private 으로 뺀 것.
    // 5xx(Cloudinary 쪽 실패)는 MEDIA_STORAGE_FAILED, 그 외 200이 아닌 상태코드(404 등, 리소스가
    // 실제로 없거나 서명이 잘못된 경우)는 MEDIA_NOT_FOUND, 타임아웃/연결 실패 같은 RestClientException 은
    // MEDIA_STORAGE_FAILED — HttpClient 시절엔 200 아니면 전부 NOT_FOUND였는데, 500까지 NOT_FOUND로
    // 뭉뚱그리는 게 부정확해서 바로잡음(단순 클라이언트 교체가 아니라 의도적 동작 변경).
    Resource fetchResource(String url, String filename) {
        byte[] body;
        try {
            body = restClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
                    })
                    .onStatus(status -> status.value() != 200, (req, res) -> {
                        throw new BusinessException(ErrorCode.MEDIA_NOT_FOUND);
                    })
                    .body(byte[].class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
        // RestClient 는 응답 바디가 없으면 null 을 돌려주지만 ByteArrayResource 는 null을 못 받아 가드
        if (body == null) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
        return new ByteArrayResource(body) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
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

    // 업로드 시 Cloudinary 에 넘길 folder 파라미터 = rootFolder + "/" + policy.folder (rootFolder 비면 policy.folder 그대로)
    private String uploadFolderFor(StoragePolicy policy) {
        String root = properties.cloudinary().rootFolder();
        return (root == null || root.isBlank()) ? policy.folder() : root + "/" + policy.folder();
    }

    // "go-mmit/check-ins/abc.jpg" -> "abc.jpg".
    static String filenameOf(String storageKey) {
        return storageKey.substring(storageKey.lastIndexOf('/') + 1);
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

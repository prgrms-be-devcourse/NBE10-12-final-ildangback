package com.gommit.domain.media.config;

import com.gommit.domain.media.policy.StoragePolicy.Visibility;
import java.net.URI;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// provider=local 일 때 PUBLIC 미디어를 파일시스템에서 정적 서빙(HTTP 응답에 반환)하도록 URL -> 폴더 매핑 등록
//   GET {baseUrl 의 path}/{folder}/**  ->  {baseDir}/{folder}/ 의 파일
@Configuration
@ConditionalOnProperty(name = "media.storage.provider", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class MediaLocalResourceConfig implements WebMvcConfigurer {

    private final MediaStorageProperties properties;

    // PUBLIC 정책 folder 마다 "URL 패턴 -> 디스크 폴더" 리소스 핸들러를 등록한다.
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = URI.create(properties.local().baseUrl()).getPath().replaceAll("/$", "");
        Path baseDir = Path.of(properties.local().baseDir()).toAbsolutePath().normalize();

        properties.policies().values().stream()
                .filter(policy -> policy.visibility() == Visibility.PUBLIC)
                .forEach(policy -> registry.addResourceHandler(basePath + "/" + policy.folder() + "/**")
                        .addResourceLocations("file:" + baseDir.resolve(policy.folder()) + "/"));
    }
}

package com.gommit.domain.media.config;

import com.gommit.domain.media.service.CloudinaryStorageService;
import com.gommit.domain.media.service.LocalStorageService;
import com.gommit.domain.media.service.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// media.storage.provider 값에 따라 StorageService 구현을 local과 cloudinary 중 하나로 등록
@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaConfig {

    @Bean
    @ConditionalOnProperty(name = "media.storage.provider", havingValue = "local", matchIfMissing = true)
    public StorageService localStorageService(MediaStorageProperties properties) {
        return new LocalStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "media.storage.provider", havingValue = "cloudinary")
    public StorageService cloudinaryStorageService(MediaStorageProperties properties) {
        return new CloudinaryStorageService(CloudinaryClientFactory.create(properties.cloudinary()), properties);
    }
}

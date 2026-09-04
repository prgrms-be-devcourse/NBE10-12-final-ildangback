package com.gommit.global.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증 businessDate('오늘') 는 배포 환경 TZ 와 무관하게 한국 날짜로 고정한다.
    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Seoul}") String zoneId) {
        return Clock.system(ZoneId.of(zoneId));
    }
}

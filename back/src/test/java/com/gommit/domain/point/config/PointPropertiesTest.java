package com.gommit.domain.point.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("PointProperties — point.* 바인딩")
class PointPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @EnableConfigurationProperties(PointProperties.class)
    static class Config {}

    @Test
    @DisplayName("yml 키가 없으면 @DefaultValue 로 바인딩된다")
    void defaults() {
        runner.run(context -> {
            PointProperties props = context.getBean(PointProperties.class);
            assertThat(props.checkInReward()).isEqualTo(10);
            assertThat(props.groupDailyAllComplete()).isEqualTo(5);
            assertThat(props.mergeBonus()).isZero();
            assertThat(props.backgroundPurchase()).isZero();
        });
    }

    @Test
    @DisplayName("yml 값이 있으면 override 된다")
    void override() {
        runner.withPropertyValues("point.check-in-reward=7", "point.group-daily-all-complete=20")
                .run(context -> {
                    PointProperties props = context.getBean(PointProperties.class);
                    assertThat(props.checkInReward()).isEqualTo(7);
                    assertThat(props.groupDailyAllComplete()).isEqualTo(20);
                });
    }
}

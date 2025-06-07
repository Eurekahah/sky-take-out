// src/test/java/com/sky/test/config/TestConfig.java
package com.sky.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@TestConfiguration
@Profile("test")
@TestPropertySource(locations = "classpath:application-test.yml")
public class TestConfig {

    /**
     * 固定时钟 - 用于测试时间相关的逻辑
     */
    @Bean
    @Primary
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2024-01-01T12:00:00Z"), ZoneId.systemDefault());
    }
}



// src/test/java/com/sky/test/BaseIntegrationTest.java
package com.sky;

import com.sky.config.TestConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * 集成测试基类
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestConfig.class)
public abstract class BaseIntegrationTest {

    @LocalServerPort
    protected int port;

    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }
}
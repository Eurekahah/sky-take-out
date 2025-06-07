// src/test/java/com/sky/test/BaseTest.java
package com.sky;

import com.sky.config.TestConfig;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测试基类 - 提供通用的测试配置
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
@Transactional
public abstract class BaseTest {

    // 可以在这里定义通用的测试方法和工具
}
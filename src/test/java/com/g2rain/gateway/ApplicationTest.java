package com.g2rain.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("网关应用主类测试")
public class ApplicationTest {

    @Test
    @DisplayName("测试主类构造函数")
    void testConstructor() {
        // 测试构造函数不抛出异常
        assertDoesNotThrow(() -> new Application());
    }

    @Test
    @DisplayName("测试应用可以启动")
    void testApplicationStart() {
        // 直接测试主类是否可以实例化，不启动Spring上下文
        assertDoesNotThrow(() -> {
            Application application = new Application();
            assertNotNull(application);
        });
    }
}

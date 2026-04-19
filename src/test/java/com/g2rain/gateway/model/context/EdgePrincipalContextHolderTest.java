package com.g2rain.gateway.model.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("边缘主体上下文持有者测试")
public class EdgePrincipalContextHolderTest {

    @Test
    @DisplayName("测试构造函数私有性")
    void testConstructorIsPrivate() throws Exception {
        // 获取私有构造函数
        java.lang.reflect.Constructor<EdgePrincipalContextHolder> constructor =
            EdgePrincipalContextHolder.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));

        // 确保可以访问私有构造函数
        constructor.setAccessible(true);

        // 尝试创建实例应该成功（虽然不建议在实际代码中使用）
        assertDoesNotThrow(() -> constructor.newInstance());
    }

    @Test
    @DisplayName("测试获取上下文")
    void testGetContext() {
        // 执行测试
        Mono<EdgePrincipalContext> contextMono = EdgePrincipalContextHolder.get();

        List<EdgePrincipalContext> results = new ArrayList<>();
        contextMono.subscribe(results::add);

        assertFalse(results.isEmpty());
        assertNotNull(results.getFirst());
    }
}

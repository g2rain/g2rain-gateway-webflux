package com.g2rain.gateway.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("网关常量测试")
public class ConstantsTest {

    @Test
    @DisplayName("测试常量值")
    void testConstantsValues() {
        assertEquals("Authorization", Constants.AUTHORIZATION_HEADER);
        assertEquals("DPoP", Constants.CLIENT_PROOF_HEADER);
        assertEquals("dpop+jwt", Constants.CLIENT_PROOF_JWT_TYPE);
    }

    @Test
    @DisplayName("测试构造函数私有性")
    void testConstructorIsPrivate() throws Exception {
        // 获取私有构造函数
        java.lang.reflect.Constructor<Constants> constructor = Constants.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));

        // 确保可以访问私有构造函数
        constructor.setAccessible(true);

        // 尝试创建实例应该成功（虽然不建议在实际代码中使用）
        assertDoesNotThrow(() -> constructor.newInstance());
    }
}

package com.g2rain.gateway.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RFC3986测试")
public class RFC3986Test {

    @Test
    @DisplayName("测试GEN_DELIMITERS编码器")
    void testGenDelimiters() {
        assertNotNull(RFC3986.GEN_DELIMITERS);
    }

    @Test
    @DisplayName("测试SUB_DELIMITERS编码器")
    void testSubDelimiters() {
        assertNotNull(RFC3986.SUB_DELIMITERS);
    }

    @Test
    @DisplayName("测试RESERVED编码器")
    void testReserved() {
        assertNotNull(RFC3986.RESERVED);
    }

    @Test
    @DisplayName("测试UNRESERVED编码器")
    void testUnreserved() {
        assertNotNull(RFC3986.UNRESERVED);
    }

    @Test
    @DisplayName("测试P_CHAR编码器")
    void testPChar() {
        assertNotNull(RFC3986.P_CHAR);
    }

    @Test
    @DisplayName("测试SEGMENT编码器")
    void testSegment() {
        assertNotNull(RFC3986.SEGMENT);
    }

    @Test
    @DisplayName("测试SEGMENT_NZ_NC编码器")
    void testSegmentNzNc() {
        assertNotNull(RFC3986.SEGMENT_NZ_NC);
    }

    @Test
    @DisplayName("测试PATH编码器")
    void testPath() {
        assertNotNull(RFC3986.PATH);
    }

    @Test
    @DisplayName("测试QUERY编码器")
    void testQuery() {
        assertNotNull(RFC3986.QUERY);
    }

    @Test
    @DisplayName("测试FRAGMENT编码器")
    void testFragment() {
        assertNotNull(RFC3986.FRAGMENT);
    }

    @Test
    @DisplayName("测试QUERY_PARAM_VALUE编码器")
    void testQueryParamValue() {
        assertNotNull(RFC3986.QUERY_PARAM_VALUE);
    }

    @Test
    @DisplayName("测试QUERY_PARAM_VALUE_STRICT编码器")
    void testQueryParamValueStrict() {
        assertNotNull(RFC3986.QUERY_PARAM_VALUE_STRICT);
    }

    @Test
    @DisplayName("测试QUERY_PARAM_NAME编码器")
    void testQueryParamName() {
        assertNotNull(RFC3986.QUERY_PARAM_NAME);
    }

    @Test
    @DisplayName("测试QUERY_PARAM_NAME_STRICT编码器")
    void testQueryParamNameStrict() {
        assertNotNull(RFC3986.QUERY_PARAM_NAME_STRICT);
    }

    @Test
    @DisplayName("测试构造函数私有性")
    void testConstructorIsPrivate() throws Exception {
        // 获取私有构造函数
        java.lang.reflect.Constructor<RFC3986> constructor = RFC3986.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));

        // 确保可以访问私有构造函数
        constructor.setAccessible(true);

        // 尝试创建实例应该成功（虽然不建议在实际代码中使用）
        assertDoesNotThrow(() -> constructor.newInstance());
    }
}

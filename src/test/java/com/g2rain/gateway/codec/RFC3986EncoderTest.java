package com.g2rain.gateway.codec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RFC3986编码器测试")
public class RFC3986EncoderTest {

    @Test
    @DisplayName("测试默认编码方法")
    void testEncode() {
        String input = "hello world";
        String expected = "hello%20world";
        String result = RFC3986Encoder.encode(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("测试带字符集的编码方法")
    void testEncodeWithCharset() {
        String input = "hello world";
        String expected = "hello%20world";
        String result = RFC3986Encoder.encode(input, StandardCharsets.UTF_8);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("测试特殊字符编码")
    void testSpecialCharactersEncoding() {
        String input = "hello@world.com";
        // 根据RFC3986规范和当前实现，@字符在查询参数中是安全的，不需要编码
        String expected = "hello@world.com";
        String result = RFC3986Encoder.encode(input);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("测试空字符串编码")
    void testEmptyStringEncoding() {
        String input = "";
        String result = RFC3986Encoder.encode(input);
        assertEquals("", result);
    }

    @Test
    @DisplayName("测试null输入")
    void testNullInput() {
        String result = RFC3986Encoder.encode(null);
        assertNull(result);
    }

    @Test
    @DisplayName("测试构造函数私有性")
    void testConstructorIsPrivate() throws Exception {
        // 获取私有构造函数
        java.lang.reflect.Constructor<RFC3986Encoder> constructor = RFC3986Encoder.class.getDeclaredConstructor();
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));

        // 确保可以访问私有构造函数
        constructor.setAccessible(true);

        // 尝试创建实例应该成功（虽然不建议在实际代码中使用）
        assertDoesNotThrow(() -> constructor.newInstance());
    }
}

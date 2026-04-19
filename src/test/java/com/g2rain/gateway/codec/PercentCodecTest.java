package com.g2rain.gateway.codec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("百分号编码器测试")
public class PercentCodecTest {

    private PercentCodec percentCodec;

    @BeforeEach
    void setUp() {
        percentCodec = new PercentCodec();
    }

    @Test
    @DisplayName("测试构造函数")
    void testConstructor() {
        assertNotNull(percentCodec);

        BitSet bitSet = new BitSet();
        PercentCodec codecWithBitSet = new PercentCodec(bitSet);
        assertNotNull(codecWithBitSet);
    }

    @Test
    @DisplayName("测试添加安全字符")
    void testAddSafe() {
        // 通过反射获取safeCharacters字段
        try {
            var field = PercentCodec.class.getDeclaredField("safeCharacters");
            field.setAccessible(true);
            BitSet bitSet = (BitSet) field.get(percentCodec);

            // 初始状态，字符'a'不应该在安全字符中
            assertFalse(bitSet.get('a'));

            // 添加安全字符
            percentCodec.addSafe('a');

            // 现在字符'a'应该在安全字符中
            assertTrue(bitSet.get('a'));
        } catch (Exception e) {
            fail("反射访问字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试移除安全字符")
    void testRemoveSafe() {
        // 先添加再移除
        percentCodec.addSafe('b');

        try {
            var field = PercentCodec.class.getDeclaredField("safeCharacters");
            field.setAccessible(true);
            BitSet bitSet = (BitSet) field.get(percentCodec);

            // 字符'b'应该在安全字符中
            assertTrue(bitSet.get('b'));

            // 移除安全字符
            percentCodec.removeSafe('b');

            // 字符'b'不应该在安全字符中
            assertFalse(bitSet.get('b'));
        } catch (Exception e) {
            fail("反射访问字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试克隆编码器")
    void testOf() {
        PercentCodec original = PercentCodec.of("abc");
        PercentCodec cloned = PercentCodec.of(original);

        assertNotNull(cloned);
        assertNotSame(original, cloned);
    }

    @Test
    @DisplayName("测试从字符序列创建编码器")
    void testOfCharSequence() {
        PercentCodec codec = PercentCodec.of("abc");
        assertNotNull(codec);
    }

    @Test
    @DisplayName("测试设置空格编码为加号")
    void testSetEncodeSpaceAsPlus() {
        try {
            var field = PercentCodec.class.getDeclaredField("encodeSpaceAsPlus");
            field.setAccessible(true);
            boolean initialValue = (boolean) field.get(percentCodec);

            // 初始值应该是false
            assertFalse(initialValue);

            // 设置为true
            percentCodec.setEncodeSpaceAsPlus(true);
            boolean newValue = (boolean) field.get(percentCodec);

            // 新值应该是true
            assertTrue(newValue);
        } catch (Exception e) {
            fail("反射访问字段失败: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("测试空输入编码")
    void testEncodeWithNullInput() {
        String result = percentCodec.encode(null, StandardCharsets.UTF_8, null);
        assertNull(result);
    }

    @Test
    @DisplayName("测试空字符串编码")
    void testEncodeWithEmptyString() {
        String result = percentCodec.encode("", StandardCharsets.UTF_8, null);
        assertEquals("", result);
    }
}

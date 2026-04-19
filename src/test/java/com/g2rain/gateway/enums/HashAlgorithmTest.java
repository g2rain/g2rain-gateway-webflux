package com.g2rain.gateway.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("哈希算法枚举测试")
public class HashAlgorithmTest {

    @Test
    @DisplayName("测试SHA256枚举值")
    void testSHA256() {
        HashAlgorithm sha256 = HashAlgorithm.SHA256;
        assertEquals("SHA-256", sha256.getCode());
    }

    @Test
    @DisplayName("测试存在的算法")
    void testExistingAlgorithm() {
        boolean result = HashAlgorithm.isNotExist("SHA-256");
        assertFalse(result);
    }

    @Test
    @DisplayName("测试不存在的算法")
    void testNonExistingAlgorithm() {
        boolean result = HashAlgorithm.isNotExist("MD5");
        assertTrue(result);
    }
}

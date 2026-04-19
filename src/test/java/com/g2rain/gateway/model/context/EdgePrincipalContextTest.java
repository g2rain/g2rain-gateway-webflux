package com.g2rain.gateway.model.context;

import com.g2rain.common.enums.OrganType;
import com.g2rain.common.enums.SessionType;
import com.g2rain.common.web.PrincipalHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("边缘主体上下文测试")
public class EdgePrincipalContextTest {

    private EdgePrincipalContext edgePrincipalContext;

    @BeforeEach
    void setUp() {
        edgePrincipalContext = new EdgePrincipalContext();
    }

    @Test
    @DisplayName("测试创建实例")
    void testCreateInstance() {
        EdgePrincipalContext context = EdgePrincipalContext.of();
        assertNotNull(context);
    }

    @Test
    @DisplayName("测试设置和获取参数哈希字符串")
    void testParamHashStr() {
        String paramHashStr = "test-hash";
        edgePrincipalContext.setParamHashStr(paramHashStr);
        assertEquals(paramHashStr, edgePrincipalContext.getParamHashStr());
    }

    @Test
    @DisplayName("测试设置和获取哈希算法")
    void testHashAlgorithm() {
        String hashAlgorithm = "SHA-256";
        edgePrincipalContext.setHashAlgorithm(hashAlgorithm);
        assertEquals(hashAlgorithm, edgePrincipalContext.getHashAlgorithm());
    }

    @Test
    @DisplayName("测试设置和获取区域")
    void testLocale() {
        Locale locale = Locale.CHINA;
        edgePrincipalContext.setAcceptLanguage(locale.toString());
        assertEquals(locale.toString(), edgePrincipalContext.getAcceptLanguage());
    }

    @Test
    @DisplayName("测试继承的getter和setter方法")
    void testInheritedGettersAndSetters() {
        // 测试基础属性
        edgePrincipalContext.setUserId(123L);
        assertEquals(123L, edgePrincipalContext.getUserId());

        edgePrincipalContext.setOrganType(OrganType.TENANT);
        assertEquals(OrganType.TENANT, edgePrincipalContext.getOrganType());

        edgePrincipalContext.setSessionType(SessionType.USER);
        assertEquals(SessionType.USER, edgePrincipalContext.getSessionType());
    }

    @Test
    @DisplayName("测试getValue方法")
    void testGetValue() {
        edgePrincipalContext.setUserId(123L);
        String value = edgePrincipalContext.getValue(PrincipalHeaders.USER_ID);
        assertEquals("123", value);
    }

    @Test
    @DisplayName("测试setValue方法")
    void testSetValue() {
        edgePrincipalContext.setValue(PrincipalHeaders.USER_ID, "123");
        assertEquals(123L, edgePrincipalContext.getUserId());
    }
}

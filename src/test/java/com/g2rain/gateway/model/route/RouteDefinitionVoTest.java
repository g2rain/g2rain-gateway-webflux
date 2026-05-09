package com.g2rain.gateway.model.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("路由定义实体测试")
public class RouteDefinitionVoTest {

    private RouteDefinitionVo vo;

    @BeforeEach
    void setUp() {
        vo = new RouteDefinitionVo();
    }

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        RouteDefinitionVo entity = new RouteDefinitionVo();
        assertNotNull(entity);
    }

    @Test
    @DisplayName("测试ID的getter和setter")
    void testIdGetterAndSetter() {
        Long id = 1L;
        vo.setId(id);
        assertEquals(id, vo.getId());
    }

    @Test
    @DisplayName("测试Name的getter和setter")
    void testNameGetterAndSetter() {
        String name = "test-route";
        vo.setName(name);
        assertEquals(name, vo.getName());
    }

    @Test
    @DisplayName("测试Endpoint的getter和setter")
    void testEndpointGetterAndSetter() {
        String endpoint = "http://test-service";
        vo.setEndpoint(endpoint);
        assertEquals(endpoint, vo.getEndpoint());
    }

    @Test
    @DisplayName("测试RoutePrefix的getter和setter")
    void testRoutePrefixGetterAndSetter() {
        String prefix = "api";
        vo.setRoutePrefix(prefix);
        assertEquals(prefix, vo.getRoutePrefix());
    }

    @Test
    @DisplayName("测试Path的getter和setter")
    void testPathGetterAndSetter() {
        String path = "/test/**";
        vo.setPath(path);
        assertEquals(path, vo.getPath());
    }

    @Test
    @DisplayName("测试Method的getter和setter")
    void testMethodGetterAndSetter() {
        String method = "GET";
        vo.setMethod(method);
        assertEquals(method, vo.getMethod());
    }

    @Test
    @DisplayName("未赋值字段为null")
    void testDefaults() {
        assertNull(vo.getId());
        assertNull(vo.getEndpoint());
        assertNull(vo.getRoutePrefix());
        assertNull(vo.getPath());
        assertNull(vo.getMethod());
        assertNull(vo.getName());
    }
}

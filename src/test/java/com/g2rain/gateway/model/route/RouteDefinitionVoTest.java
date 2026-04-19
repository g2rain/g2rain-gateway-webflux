package com.g2rain.gateway.model.route;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("路由定义实体测试")
public class RouteDefinitionVoTest {

    private RouteDefinitionVo RouteDefinitionVo;

    @BeforeEach
    void setUp() {
        RouteDefinitionVo = new RouteDefinitionVo();
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
        RouteDefinitionVo.setId(id);
        assertEquals(id, RouteDefinitionVo.getId());
    }

    @Test
    @DisplayName("测试Name的getter和setter")
    void testNameGetterAndSetter() {
        String name = "test-route";
        RouteDefinitionVo.setName(name);
        assertEquals(name, RouteDefinitionVo.getName());
    }

    @Test
    @DisplayName("测试EndpointHost的getter和setter")
    void testEndpointHostGetterAndSetter() {
        String endpointHost = "http://test-service";
        RouteDefinitionVo.setEndpointHost(endpointHost);
        assertEquals(endpointHost, RouteDefinitionVo.getEndpointHost());
    }

    @Test
    @DisplayName("测试EndpointPath的getter和setter")
    void testEndpointPathGetterAndSetter() {
        String endpointPath = "/api/v1/test";
        RouteDefinitionVo.setEndpointPath(endpointPath);
        assertEquals(endpointPath, RouteDefinitionVo.getEndpointPath());
    }

    @Test
    @DisplayName("测试Context的getter和setter")
    void testContextGetterAndSetter() {
        String context = "{\"key\":\"value\"}";
        RouteDefinitionVo.setContext(context);
        assertEquals(context, RouteDefinitionVo.getContext());
    }

    @Test
    @DisplayName("测试Path的getter和setter")
    void testPathGetterAndSetter() {
        String path = "/test/**";
        RouteDefinitionVo.setPath(path);
        assertEquals(path, RouteDefinitionVo.getPath());
    }

    @Test
    @DisplayName("测试Method的getter和setter")
    void testMethodGetterAndSetter() {
        String method = "GET";
        RouteDefinitionVo.setMethod(method);
        assertEquals(method, RouteDefinitionVo.getMethod());
    }

    @Test
    @DisplayName("测试HeaderParameters的getter和setter")
    void testHeaderParametersGetterAndSetter() {
        String headerParameters = "Authorization=Bearer token";
        RouteDefinitionVo.setHeaderParameters(headerParameters);
        assertEquals(headerParameters, RouteDefinitionVo.getHeaderParameters());
    }

    @Test
    @DisplayName("测试ContentType的getter和setter")
    void testContentTypeGetterAndSetter() {
        String contentType = "application/json";
        RouteDefinitionVo.setContentType(contentType);
        assertEquals(contentType, RouteDefinitionVo.getContentType());
    }
}

package com.g2rain.gateway.exception;

import com.g2rain.gateway.client.InfraServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@DisplayName("默认错误信息注册表测试")
public class ErrorMessageStorageTest {

    private ErrorMessageStorage errorMessageRegistry;
    @Mock
    private InfraServiceClient infraServiceClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(infraServiceClient.errorMessages()).thenReturn(Mono.just(java.util.List.of()));
        errorMessageRegistry = new ErrorMessageStorage(infraServiceClient);
    }

    @Test
    @DisplayName("测试加载所有错误信息")
    void testLoad() {
        // 这个方法在实现中是空操作，只需要验证不抛出异常
        assertDoesNotThrow(() -> errorMessageRegistry.load());
    }

    @Test
    @DisplayName("测试获取错误信息 - 空错误码")
    void testGetMessageWithEmptyErrorCode() {
        String result = errorMessageRegistry.getMessage("", "en_US");
        assertNull(result);
    }

    @Test
    @DisplayName("测试获取错误信息 - 空语言环境")
    void testGetMessageWithEmptyLocale() {
        String result = errorMessageRegistry.getMessage("ERROR_001", "");
        assertNull(result);
    }

    @Test
    @DisplayName("测试获取错误信息 - null语言环境")
    void testGetMessageWithNullLocale() {
        String result = errorMessageRegistry.getMessage("ERROR_001", null);
        assertNull(result);
    }

    @Test
    @DisplayName("测试获取数据源")
    void testDataSource() {
        String dataSource = errorMessageRegistry.dataSource();
        assertNotNull(dataSource);
        assertEquals("ERROR_MSG", dataSource);
    }

    @Test
    @DisplayName("测试获取值类型")
    void testGetValueType() {
        Class<?> valueType = errorMessageRegistry.getValueType();
        assertNotNull(valueType);
        assertEquals("com.g2rain.common.exception.LocalizedErrorMessage", valueType.getName());
    }
}

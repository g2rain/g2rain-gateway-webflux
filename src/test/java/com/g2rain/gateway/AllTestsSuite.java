package com.g2rain.gateway;

import com.g2rain.gateway.codec.PercentCodecTest;
import com.g2rain.gateway.codec.RFC3986EncoderTest;
import com.g2rain.gateway.codec.RFC3986Test;
import com.g2rain.gateway.enums.HashAlgorithmTest;
import com.g2rain.gateway.exception.ErrorMessageStorageTest;
import com.g2rain.gateway.exception.GlobalErrorHandlerTest;
import com.g2rain.gateway.filters.CachedBodyFilterTest;
import com.g2rain.gateway.filters.GatewayDPoPAuthFilterTest;
import com.g2rain.gateway.filters.PrincipalForwardFilterTest;
import com.g2rain.gateway.filters.ResponseAdjustFilterTest;
import com.g2rain.gateway.filters.SignVerificationFilterTest;
import com.g2rain.gateway.filters.TraceLoggingFilterTest;
import com.g2rain.gateway.matcher.MatcherUtilsTest;
import com.g2rain.gateway.model.context.EdgePrincipalContextHolderTest;
import com.g2rain.gateway.model.context.EdgePrincipalContextTest;
import com.g2rain.gateway.model.route.RouteDefinitionVoTest;
import com.g2rain.gateway.model.web.CachedBodyRequestTest;
import com.g2rain.gateway.model.web.CachedBodyResponseTest;
import com.g2rain.gateway.route.MemoryRouteLoaderTest;
import com.g2rain.gateway.route.MemoryRouteRepositoryTest;
import com.g2rain.gateway.utils.ConstantsTest;
import com.g2rain.gateway.utils.ReqParamCodecTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("网关模块测试套件")
@SelectClasses({
    ApplicationTest.class,
    GatewayDPoPAuthFilterTest.class,
    ConstantsTest.class,
    MemoryRouteRepositoryTest.class,
    MemoryRouteLoaderTest.class,
    PercentCodecTest.class,
    HashAlgorithmTest.class,
    ReqParamCodecTest.class,
    CachedBodyRequestTest.class,
    CachedBodyResponseTest.class,
    CachedBodyFilterTest.class,
    PrincipalForwardFilterTest.class,
    ResponseAdjustFilterTest.class,
    SignVerificationFilterTest.class,
    TraceLoggingFilterTest.class,
    MatcherUtilsTest.class,
    RFC3986EncoderTest.class,
    RFC3986Test.class,
    ErrorMessageStorageTest.class,
    GlobalErrorHandlerTest.class,
    EdgePrincipalContextTest.class,
    EdgePrincipalContextHolderTest.class,
    RouteDefinitionVoTest.class
})
public class AllTestsSuite {
}

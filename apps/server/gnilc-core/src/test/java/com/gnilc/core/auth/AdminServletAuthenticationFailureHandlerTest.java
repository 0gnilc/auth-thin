package com.gnilc.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnilc.auth.authn.handler.AuthenticationResult;
import com.gnilc.auth.authn.servlet.context.ServletAuthenticationContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证内部认证异常输出通用中文错误，且不暴露异常细节。 */
class AdminServletAuthenticationFailureHandlerTest {

    @Test
    void internalAuthenticationErrorReturnsChineseGenericFailure() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN");
        request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletAuthenticationContext context = new ServletAuthenticationContext(request, response);

        new AdminServletAuthenticationFailureHandler().handle(
                context,
                AuthenticationResult.failed(null, new IllegalStateException("backend unavailable")));

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(body.get("code").asInt()).isEqualTo(20002);
        assertThat(body.get("data").isNull()).isTrue();
        assertThat(body.get("error").asText()).isEqualTo("认证失败。");
        assertThat(body.get("message").asText()).isEqualTo("认证失败。");
    }

    @Test
    void missingLanguageHeaderStillReturnsChinese() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AdminServletAuthenticationFailureHandler().handle(
                new ServletAuthenticationContext(request, response),
                AuthenticationResult.failed(null));

        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("认证失败。");
        assertThat(body.get("message").asText()).isEqualTo("认证失败。");
    }

}

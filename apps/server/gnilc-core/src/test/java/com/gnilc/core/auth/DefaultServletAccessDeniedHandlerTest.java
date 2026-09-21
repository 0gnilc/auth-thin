package com.gnilc.core.auth;

import com.gnilc.auth.authz.context.AccessContext;
import com.gnilc.auth.authz.context.AccessIdentity;
import com.gnilc.auth.authz.context.AccessTarget;
import com.gnilc.auth.authz.denied.AccessDeniedContext;
import com.gnilc.auth.authz.servlet.context.ServletAccessDeniedContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仅为尚未提交的 Servlet 响应写中文 403 JSON，不覆盖已提交响应。 */
class DefaultServletAccessDeniedHandlerTest {
    @Test
    void writesJson403OnlyForOpenServletResponse() throws Exception {
        DefaultServletAccessDeniedHandler handler = new DefaultServletAccessDeniedHandler(
                );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN");
        request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServletAccessDeniedContext deniedContext = new ServletAccessDeniedContext(
                request, response, (req, res) -> { });
        AccessContext access = new AccessContext(
                new AccessIdentity("1", Map.of()), new AccessTarget("/private", "GET"));

        assertThat(handler.supports(access, deniedContext)).isTrue();
        handler.handle(access, deniedContext);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":20003", "\"error\":\"访问被拒绝。\"");
        assertThat(handler.supports(access, new AccessDeniedContext() { })).isFalse();
    }

}

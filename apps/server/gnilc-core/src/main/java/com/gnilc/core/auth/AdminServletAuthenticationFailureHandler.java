package com.gnilc.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnilc.auth.authn.handler.AuthenticationResult;
import com.gnilc.auth.authn.servlet.context.ServletAuthenticationContext;
import com.gnilc.auth.authn.servlet.handler.ServletAuthenticationFailureHandler;
import com.gnilc.common.constant.ResponseCode;
import com.gnilc.common.utils.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 后台管理员 Servlet 认证失败响应。
 */
@Component
public class AdminServletAuthenticationFailureHandler implements ServletAuthenticationFailureHandler {
    private static final String DEFAULT_MESSAGE = "认证失败。";
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 将认证失败写成 HTTP 401 和对应业务错误码；无公开失败原因时提供通用中文提示。 */
    @Override
    public void handle(ServletAuthenticationContext context, AuthenticationResult result) {
        HttpServletResponse response = context.getResponse();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(JSON_CONTENT_TYPE);
        try {
            String message = resolveMessage(result);
            response.getWriter().write(OBJECT_MAPPER.writeValueAsString(
                    R.error(ResponseCode.UNAUTHORIZED, message)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write authentication failure response", exception);
        }
    }

    private String resolveMessage(AuthenticationResult result) {
        // 显式业务原因可以展示；异常对象不写入响应，避免将内部失败细节泄露给调用方。
        if (result != null && StringUtils.hasText(result.getReason())) {
            return result.getReason();
        }
        return DEFAULT_MESSAGE;
    }
}

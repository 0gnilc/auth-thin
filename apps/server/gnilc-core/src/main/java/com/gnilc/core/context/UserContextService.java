package com.gnilc.core.context;

import com.gnilc.auth.authn.context.AccessPrincipal;
import com.gnilc.auth.authn.servlet.context.DefaultAccessPrincipalHolder;
import com.gnilc.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** 提供当前执行环境中的可信 Access Identity。 */
@Service
public class UserContextService {

    /** 获取当前已认证 RBAC User ID。 */
    public Long getUserId() {
        Long userId = findUserId();
        Preconditions.checkArgument(userId != null,
                "登录已过期，请重新登录。");
        return userId;
    }

    /** 获取可选的当前 RBAC User ID；匿名请求返回 {@code null}。 */
    public Long findUserId() {
        AccessPrincipal principal = DefaultAccessPrincipalHolder.getPrincipal();
        if (principal == null) {
            return null;
        }
        String identifier = principal.getIdentifier();
        if (StringUtils.isBlank(identifier)) {
            return null;
        }
        return Long.valueOf(identifier);
    }
}

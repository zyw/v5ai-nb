package xin.v5ai.nb.api.auth;

import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 管理 API 认证与 RBAC：
 * - /api/admin/** 必须登录（/api/auth/login 除外）；
 * - 非 GET 写操作要求 ADMIN 角色（角色在登录时写入 Sa-Token session）；
 * - 未登录 / 无权限异常由 {@link xin.v5ai.nb.api.error.GlobalExceptionHandler} 统一映射为 JSON。
 */
@Configuration
public class SaTokenWebConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(invocation -> {
            StpUtil.checkLogin();
            var request = cn.dev33.satoken.context.SaHolder.getRequest();
            if (isWriteMethod(request.getMethod()) && !hasRole("ADMIN")) {
                throw new NotRoleException("admin");
            }
        }))
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/auth/login");
    }

    private static boolean isWriteMethod(String method) {
        return !"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method);
    }

    @SuppressWarnings("unchecked")
    static boolean hasRole(String role) {
        var session = StpUtil.getSession(false);
        if (session == null) {
            return false;
        }
        Object roles = session.get("roles");
        return roles instanceof List<?> list && list.contains(role);
    }
}

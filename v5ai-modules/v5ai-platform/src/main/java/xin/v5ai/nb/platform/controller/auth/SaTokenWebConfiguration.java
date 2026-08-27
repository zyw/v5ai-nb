//package xin.v5ai.nb.platform.auth;
//
//import cn.dev33.satoken.context.SaHolder;
//import cn.dev33.satoken.exception.NotRoleException;
//import cn.dev33.satoken.interceptor.SaInterceptor;
//import cn.dev33.satoken.stp.StpUtil;
//import jakarta.servlet.DispatcherType;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.HandlerInterceptor;
//import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//import java.util.List;
//
///**
// * 管理 API 认证与 RBAC：
// * - /api/admin/** 必须登录（/api/auth/login 除外）；
// * - 非 GET 写操作要求 ADMIN 角色（角色在登录时写入 Sa-Token session）；
// * - 未登录 / 无权限异常由 API 层异常处理器统一映射为 JSON。
// *
// * <p>异步分发说明：SSE 流式端点（如 Agent 调试）返回响应式流时，Servlet 容器会发起
// * ASYNC 分发并再次执行拦截器链；此时异步线程中 Sa-Token 的 ThreadLocal 上下文尚未初始化，
// * 再次 {@code checkLogin} 会抛 SaTokenContextException。鉴权已在首次 REQUEST 分发完成，
// * 因此拦截器直接跳过 ASYNC 分发。</p>
// */
//@Configuration
//public class SaTokenWebConfiguration implements WebMvcConfigurer {
//
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(adminAuthInterceptor())
//                .addPathPatterns("/api/admin/**")
//                .excludePathPatterns("/api/auth/login");
//    }
//
//    HandlerInterceptor adminAuthInterceptor() {
//        return new SaInterceptor(invocation -> {
//            StpUtil.checkLogin();
//            var request = SaHolder.getRequest();
//            if (isWriteMethod(request.getMethod()) && !hasRole("ADMIN")) {
//                throw new NotRoleException("admin");
//            }
//        }) {
//            @Override
//            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
//                    throws Exception {
//                if (request.getDispatcherType() == DispatcherType.ASYNC) {
//                    return true;
//                }
//                return super.preHandle(request, response, handler);
//            }
//        };
//    }
//
//    private static boolean isWriteMethod(String method) {
//        return !"GET".equalsIgnoreCase(method) && !"OPTIONS".equalsIgnoreCase(method);
//    }
//
//    static boolean hasRole(String role) {
//        var session = StpUtil.getSession(false);
//        if (session == null) {
//            return false;
//        }
//        Object roles = session.get("roles");
//        return roles instanceof List<?> list && list.contains(role);
//    }
//}

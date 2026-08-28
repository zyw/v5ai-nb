//package xin.v5ai.nb.platform.service.impl;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.boot.ApplicationArguments;
//import org.springframework.boot.ApplicationRunner;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//import xin.v5ai.nb.common.core.platform.RoleKey;
//import xin.v5ai.nb.common.core.platform.UserAccountStore;
//
///**
// * 启动种子：无任何用户时创建默认管理员 admin/admin（挂到 default 租户、admin 角色）。
// * 生产环境应通过管理 API 修改密码或更换账号。
// */
//@Component
//public class PlatformDataInitializer implements ApplicationRunner {
//    private static final Logger log = LoggerFactory.getLogger(PlatformDataInitializer.class);
//
//    private final UserAccountStore userAccountStore;
//    private final PasswordEncoder passwordEncoder;
//
//    public PlatformDataInitializer(UserAccountStore userAccountStore, PasswordEncoder passwordEncoder) {
//        this.userAccountStore = userAccountStore;
//        this.passwordEncoder = passwordEncoder;
//    }
//
//    @Override
//    public void run(ApplicationArguments args) {
//        try {
//            if (userAccountStore.list().isEmpty()) {
//                userAccountStore.create("admin", passwordEncoder.encode("admin"), "Platform Admin", null,
//                        java.util.List.of(RoleKey.ADMIN.name()));
//                log.warn("Seeded default administrator account 'admin/admin'. Change the password in production.");
//            }
//        } catch (Exception exception) {
//            // 无用户表的环境（如 H2 测试上下文）跳过种子，不阻塞应用启动
//            log.warn("Skipped platform data seeding ({}): {}", exception.getClass().getSimpleName(),
//                    exception.getMessage());
//        }
//    }
//}

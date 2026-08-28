//package xin.v5ai.nb.common.core.platform;
//
//import java.time.Instant;
//
///**
// * 菜单领域模型（页面权限的资源单元）。
// *
// * @param id        主键
// * @param parentId  父菜单 id（顶级菜单为 null）
// * @param code      稳定标识（唯一，用于种子数据与前端图标映射）
// * @param name      显示名
// * @param type      类型：DIR（分组，无路由）/ MENU（页面，绑定 routeName）
// * @param routeName vue-router 路由名（仅 MENU）
// * @param path      参考展示路径（可为 null）
// * @param icon      图标名（前端映射为 lucide 组件）
// * @param sortOrder 排序
// * @param visible   是否在导航中显示
// * @param createdAt 创建时间
// * @param updatedAt 更新时间
// */
//public record Menu(
//        Long id,
//        Long parentId,
//        String code,
//        String name,
//        String type,
//        String routeName,
//        String path,
//        String icon,
//        int sortOrder,
//        boolean visible,
//        Instant createdAt,
//        Instant updatedAt
//) {
//    public boolean isDirectory() {
//        return "DIR".equalsIgnoreCase(type);
//    }
//}

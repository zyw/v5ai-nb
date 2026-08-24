package xin.v5ai.nb.platform.controller;

import org.springframework.web.bind.annotation.*;
import xin.v5ai.nb.common.core.domain.R;
import xin.v5ai.nb.common.core.page.PageResult;
import xin.v5ai.nb.common.core.platform.Menu;
import xin.v5ai.nb.common.core.platform.MenuStore;
import xin.v5ai.nb.common.log.annotation.Log;
import xin.v5ai.nb.common.log.enums.BusinessType;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;

/**
 * 菜单管理 API（菜单树 CRUD）。
 * 菜单为全局资源；DIR 为顶级分组、MENU 为绑定路由的页面节点。含子节点的菜单不可删除。
 */
@RestController
@RequestMapping("/api/admin/menus")
public class MenuController {
    private static final String DIR = "DIR";
    private static final String MENU = "MENU";

    private final MenuStore menuStore;

    public MenuController(MenuStore menuStore) {
        this.menuStore = menuStore;
    }

    public record CreateMenuRequest(Long parentId, String code, String name, String type,
                                    String routeName, String path, String icon,
                                    Integer sortOrder, Boolean visible) {
    }

    public record UpdateMenuRequest(Long parentId, String name, String type,
                                    String routeName, String path, String icon,
                                    Integer sortOrder, Boolean visible) {
    }

    @GetMapping
    public R<PageResult<Menu>> list(PageQuery pageQuery) {
//      TODO PageUtils.page  return R.ok(PageUtils.page(menuStore.list(), pageQuery));
        return R.ok();
    }

    @PostMapping
    @Log(title = "新建菜单", businessType = BusinessType.INSERT)
    public R<Menu> create(@RequestBody CreateMenuRequest request) {
        var code = normalizeCode(request.code());
        var name = requireText(request.name(), "name is required");
        var type = normalizeType(request.type());
        var routeName = blankToNull(request.routeName());
        var parentId = request.parentId();

        if (menuStore.findByCode(code) != null) {
            throw new IllegalArgumentException("menu code already exists: " + code);
        }
        validateShape(null, parentId, type, routeName);

        var menu = menuStore.create(parentId, code, name, type, routeName,
                blankToNull(request.path()), blankToNull(request.icon()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.visible() == null || request.visible());
        return R.ok(menu);
    }

    @PutMapping("/{id}")
    @Log(title = "更新菜单", businessType = BusinessType.UPDATE)
    public R<Menu> update(@PathVariable("id") Long id, @RequestBody UpdateMenuRequest request) {
        var existing = menuStore.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("menu does not exist: " + id);
        }
        var name = requireText(request.name(), "name is required");
        var type = normalizeType(request.type());
        var routeName = blankToNull(request.routeName());
        var parentId = request.parentId();

        if (id.equals(parentId)) {
            throw new IllegalArgumentException("menu cannot be its own parent");
        }
        validateShape(id, parentId, type, routeName);

        var menu = menuStore.update(id, parentId, name, type, routeName,
                blankToNull(request.path()), blankToNull(request.icon()),
                request.sortOrder() == null ? 0 : request.sortOrder(),
                request.visible() == null || request.visible());
        return R.ok(menu);
    }

    @DeleteMapping("/{id}")
    @Log(title = "删除菜单", businessType = BusinessType.DELETE)
    public R<Void> delete(@PathVariable("id") Long id) {
        var existing = menuStore.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("menu does not exist: " + id);
        }
        if (menuStore.countChildren(id) > 0) {
            throw new IllegalArgumentException("menu still has children: " + existing.code());
        }
        menuStore.delete(id);
        return R.ok();
    }

    /**
     * DIR 仅可位于顶级（parentId 为 null）；MENU 可顶级或挂到某个 DIR 下。
     * DIR 不携带 routeName，MENU 必填 routeName（唯一性由数据库约束兜底，这里做友好提示）。
     */
    private void validateShape(Long currentId, Long parentId, String type, String routeName) {
        if (parentId != null) {
            var parent = menuStore.findById(parentId);
            if (parent == null || !parent.isDirectory()) {
                throw new IllegalArgumentException("parent must be an existing directory menu");
            }
        }
        if (DIR.equals(type)) {
            if (parentId != null) {
                throw new IllegalArgumentException("directory menu must be top-level");
            }
            if (routeName != null) {
                throw new IllegalArgumentException("directory menu cannot have routeName");
            }
        } else {
            if (routeName == null) {
                throw new IllegalArgumentException("routeName is required for a page menu");
            }
            var duplicated = menuStore.list().stream()
                    .anyMatch(m -> routeName.equals(m.routeName()) && !m.id().equals(currentId));
            if (duplicated) {
                throw new IllegalArgumentException("routeName already in use: " + routeName);
            }
        }
    }

    private static String normalizeType(String type) {
        var value = blankToNull(type);
        if (value == null) {
            return MENU;
        }
        value = value.toUpperCase();
        if (!DIR.equals(value) && !MENU.equals(value)) {
            throw new IllegalArgumentException("type must be DIR or MENU");
        }
        return value;
    }

    private static String normalizeCode(String code) {
        var value = blankToNull(code);
        if (value == null) {
            throw new IllegalArgumentException("code is required");
        }
        if (!value.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("code must be lowercase letters, digits or dash");
        }
        return value;
    }

    private static String requireText(String value, String message) {
        var result = blankToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(message);
        }
        return result;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

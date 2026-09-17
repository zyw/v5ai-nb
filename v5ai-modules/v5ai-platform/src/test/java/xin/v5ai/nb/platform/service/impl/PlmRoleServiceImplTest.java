package xin.v5ai.nb.platform.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import io.github.linpeilie.Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import xin.v5ai.nb.platform.domain.PlmRole;
import xin.v5ai.nb.platform.domain.bo.PlmRoleBo;
import xin.v5ai.nb.platform.mapper.PlmRoleMapper;
import xin.v5ai.nb.platform.mapper.PlmRoleMenuMapper;
import xin.v5ai.nb.platform.mapper.PlmUserRoleMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * {@link PlmRoleServiceImpl} 的 Mockito 单测。
 * <p>新增角色弹框不含菜单树，menuIds 为可选入参；缺失时新增必须成功且不写菜单关联，
 * 否则会在 {@code insertRoleMenu} 遍历 {@code Long[] menuIds} 时抛 NullPointerException。</p>
 *
 * @author ZYW
 * @since 2026-09-17
 */
class PlmRoleServiceImplTest {

    private PlmRoleMapper roleMapper;
    private PlmRoleMenuMapper roleMenuMapper;
    private PlmRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        // MapstructUtils 在类加载时通过 SpringUtil 取 Converter Bean，
        // 注册一个按属性名拷贝的 Converter 使 BO 转换可独立测试。
        var context = new StaticApplicationContext();
        context.getBeanFactory().registerSingleton("converter", new CopyConverter());
        context.refresh();
        new SpringUtil().setApplicationContext(context);

        roleMapper = mock(PlmRoleMapper.class);
        roleMenuMapper = mock(PlmRoleMenuMapper.class);
        service = new PlmRoleServiceImpl(roleMapper, roleMenuMapper, mock(PlmUserRoleMapper.class));
    }

    @Test
    void insertRoleWithoutMenuIdsSkipsMenuBinding() {
        assertThat(service.insertRole(roleBo())).isEqualTo(1);

        verify(roleMapper).insert(any(PlmRole.class));
        verifyNoInteractions(roleMenuMapper);
    }

    @Test
    void insertRoleWithMenuIdsBindsMenus() {
        var bo = roleBo();
        bo.setMenuIds(new Long[]{1L, 2L});

        service.insertRole(bo);

        verify(roleMenuMapper).insertBatch(argThat(rows -> rows.size() == 2));
    }

    private static PlmRoleBo roleBo() {
        var bo = new PlmRoleBo();
        bo.setRoleName("测试角色");
        bo.setRoleKey("test:role");
        bo.setRoleSort(1);
        return bo;
    }

    /**
     * 按属性名拷贝的 Converter：单测中替代 mapstruct-plus 的 Spring 装配。
     */
    private static class CopyConverter extends Converter {
        @Override
        public <S, T> T convert(S source, Class<T> desc) {
            if (source == null) {
                return null;
            }
            try {
                T target = desc.getDeclaredConstructor().newInstance();
                BeanUtil.copyProperties(source, target);
                return target;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
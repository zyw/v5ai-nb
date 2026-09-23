package xin.v5ai.nb.platform.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.annotation.IdType;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户/角色/菜单/客户端四张 RBAC 表的主键必须是数据库自增（BIGSERIAL + {@link IdType#AUTO}），
 * 不得回落到全局配置 common-mybatis.yml 的 idType=ASSIGN_ID（雪花 ID）。
 * 雪花 ID 超出前端 JS 安全整数范围，会导致列表/编辑按 id 操作失准。
 *
 * @author ZYW
 * @since 2026-09-17
 */
class PlmEntityIdTypeTest {

    @Test
    void rbacPrimaryKeysUseDatabaseAutoIncrement() {
        Stream.of(PlmUser.class, PlmRole.class, PlmMenu.class, PlmClient.class).forEach(entity -> {
            var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, entity);
            TableInfo tableInfo = TableInfoHelper.getTableInfo(entity);
            assertThat(tableInfo).as("%s 缺少 TableInfo", entity.getSimpleName()).isNotNull();
            assertThat(tableInfo.getIdType())
                .as("%s 主键应为数据库自增（IdType.AUTO）", entity.getSimpleName())
                .isEqualTo(IdType.AUTO);
        });
    }
}
package xin.v5ai.nb.infrastructure.mcp;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity>, McpServerGateway {
    @Override
    default void insertServer(McpServerEntity entity) {
        insert(entity);
    }

    @Override
    default void updateServer(McpServerEntity entity) {
        updateById(entity);
    }

    @Override
    default McpServerEntity selectServerById(Long id) {
        return selectById(id);
    }

    @Override
    default List<McpServerEntity> selectAll() {
        return selectList(null);
    }

    @Override
    default void disableServer(Long id) {
        var entity = new McpServerEntity();
        entity.setStatus("DISABLED");
        update(entity, new LambdaUpdateWrapper<McpServerEntity>().eq(McpServerEntity::getId, id));
    }
}

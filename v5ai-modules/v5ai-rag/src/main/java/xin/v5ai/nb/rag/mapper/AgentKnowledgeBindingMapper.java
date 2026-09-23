package xin.v5ai.nb.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.rag.domain.AgentKnowledgeBinding;

/**
 * <p>
 * AgentDTO-知识库绑定 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Mapper
public interface AgentKnowledgeBindingMapper extends BaseMapper<AgentKnowledgeBinding> {
}

package xin.v5ai.nb.model.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.model.domain.V5aiModel;
import xin.v5ai.nb.model.domain.vo.V5aiModelVo;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Mapper
public interface V5aiModelMapper extends BaseMapperPlus<V5aiModel, V5aiModelVo> {
    /**
     * 校验模型是否被引用
     * @param modelId 模型ID
     * @return true: 被引用, false: 未被引用
     */
    Boolean checkAgentReferenced(Long modelId);
}
